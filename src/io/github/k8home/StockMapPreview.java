package io.github.k8home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.SurfaceTexture;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

/**
 * Hosts a rendering surface that the stock navigation draws into. Home mode is the Gen5W home map
 * widget (calls 1/2/3). Split mode is the right split-screen view that the stock RightScreenHome
 * uses (calls 22 create with width/height, 16 destroy); the navigation draws its own map, route and
 * turn arrow there. Call numbers come from com.mnsoft.navi.IInterfaceService in both apps' odex.
 */
final class StockMapPreview extends TextureView implements TextureView.SurfaceTextureListener, ServiceConnection {
    private static final String TAG = "K8HomeMap";
    private static final String DESCRIPTOR = "com.mnsoft.navi.IInterfaceService";
    private static final ComponentName RENDERER =
        new ComponentName(StockNavigation.PACKAGE, StockNavigation.PACKAGE + ".InterfaceService");
    private static final int SURFACE_CREATED = IBinder.FIRST_CALL_TRANSACTION;
    private static final int SURFACE_CHANGED = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int SURFACE_DESTROYED = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int HOME_WIDGET_FORMAT = 0x3201;
    private static final int SPLIT_CREATED = 0x16;
    private static final int SPLIT_DESTROYED = 0x10;
    /** The navigation draws the home widget at most this many pixels wide, left-aligned (measured on the
     *  K8 5W: 836 px drawn in a 911 px slot, 2026-11-22). Wider slots are filled by an even zoom. */
    private static final int HOME_DRAW_MAX_WIDTH = 836;

    private final Listener listener;
    private final boolean split;
    private IBinder service;
    private Surface surface;
    private boolean active, bound, attached, frameReady;

    interface Listener {
        void frameChanged(boolean ready);
    }

    StockMapPreview(Context context, Listener listener) {
        this(context, listener, false);
    }

    StockMapPreview(Context context, Listener listener, boolean split) {
        super(context);
        this.listener = listener;
        this.split = split;
        setOpaque(false);
        setSurfaceTextureListener(this);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        if (active) {
            try {
                bound = getContext().bindService(new Intent().setComponent(RENDERER), this, Context.BIND_AUTO_CREATE);
            } catch (SecurityException failure) {
                Log.w(TAG, "Navigation preview service not authorized", failure);
            }
        } else {
            detachSurface();
            if (bound) getContext().unbindService(this);
            bound = false;
            service = null;
        }
    }

    @Override public void onServiceConnected(ComponentName name, IBinder binder) {
        if (!active) return;
        try {
            if (!DESCRIPTOR.equals(binder.getInterfaceDescriptor())) return;
        } catch (RemoteException failure) {
            return;
        }
        service = binder;
        connectSurface();
    }

    @Override public void onServiceDisconnected(ComponentName name) {
        service = null;
        attached = false;
        setFrameReady(false);
    }

    private void fitHomeDrawing(int width, int height) {
        if (split || width <= HOME_DRAW_MAX_WIDTH || height <= 0) {
            setTransform(null);
            return;
        }
        float zoom = width / (float) HOME_DRAW_MAX_WIDTH;
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setScale(zoom, zoom, 0, height / 2f);
        setTransform(matrix);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        fitHomeDrawing(width, height);
        surface = new Surface(texture);
        connectSurface();
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        fitHomeDrawing(width, height);
        if (!attached) return;
        if (split) {
            detachSurface();
            connectSurface();
        } else {
            sendSize(width, height);
        }
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        detachSurface();
        if (surface != null) surface.release();
        surface = null;
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        if (active && attached) setFrameReady(true);
    }

    private void connectSurface() {
        if (!active || service == null || surface == null || !surface.isValid() || attached) return;
        if (split) {
            final int width = getWidth(), height = getHeight();
            if (width <= 0 || height <= 0) return;
            attached = transact(SPLIT_CREATED, new Arguments() {
                @Override public void write(Parcel parcel) {
                    parcel.writeInt(1);
                    surface.writeToParcel(parcel, 0);
                    parcel.writeInt(width);
                    parcel.writeInt(height);
                }
            });
            return;
        }
        attached = transact(SURFACE_CREATED, new Arguments() {
            @Override public void write(Parcel parcel) {
                parcel.writeInt(1);
                surface.writeToParcel(parcel, 0);
            }
        });
        if (attached) sendSize(getWidth(), getHeight());
    }

    private void sendSize(final int width, final int height) {
        if (width <= 0 || height <= 0) return;
        boolean sized = transact(SURFACE_CHANGED, new Arguments() {
            @Override public void write(Parcel parcel) {
                parcel.writeString("");
                parcel.writeInt(HOME_WIDGET_FORMAT);
                parcel.writeInt(width);
                parcel.writeInt(height);
            }
        });
        if (!sized) detachSurface();
    }

    private void detachSurface() {
        if (attached) {
            attached = false;
            transact(split ? SPLIT_DESTROYED : SURFACE_DESTROYED, new Arguments() {
                @Override public void write(Parcel parcel) {
                    parcel.writeString(split ? "" : getContext().getPackageName());
                }
            });
        }
        setFrameReady(false);
    }

    private void setFrameReady(boolean ready) {
        if (frameReady == ready) return;
        frameReady = ready;
        listener.frameChanged(ready);
    }

    private interface Arguments {
        void write(Parcel parcel);
    }

    private boolean transact(int operation, Arguments arguments) {
        if (service == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            arguments.write(data);
            if (!service.transact(operation, data, reply, 0)) return false;
            reply.readException();
            return true;
        } catch (RemoteException | RuntimeException failure) {
            Log.w(TAG, "Navigation preview unavailable", failure);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
