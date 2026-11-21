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

/** Hosts the rendering surface for the Gen5W navigation home widget. */
final class StockMapPreview extends TextureView implements TextureView.SurfaceTextureListener, ServiceConnection {
    private static final String TAG = "K8HomeMap";
    private static final String DESCRIPTOR = "com.mnsoft.navi.IInterfaceService";
    private static final ComponentName RENDERER =
        new ComponentName(StockNavigation.PACKAGE, StockNavigation.PACKAGE + ".InterfaceService");
    private static final int SURFACE_CREATED = IBinder.FIRST_CALL_TRANSACTION;
    private static final int SURFACE_CHANGED = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int SURFACE_DESTROYED = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int HOME_WIDGET_FORMAT = 0x3201;

    private final Listener listener;
    private IBinder service;
    private Surface surface;
    private boolean active, bound, attached, frameReady;

    interface Listener {
        void frameChanged(boolean ready);
    }

    StockMapPreview(Context context, Listener listener) {
        super(context);
        this.listener = listener;
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

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        connectSurface();
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (attached) sendSize(width, height);
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
            transact(SURFACE_DESTROYED, new Arguments() {
                @Override public void write(Parcel parcel) {
                    parcel.writeString(getContext().getPackageName());
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
