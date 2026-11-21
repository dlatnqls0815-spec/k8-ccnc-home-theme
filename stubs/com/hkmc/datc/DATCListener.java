package com.hkmc.datc;

public class DATCListener {
    public DATCListener() { throw new RuntimeException("stub"); }
    public void onDatcStatus(DatcStatus status) { }
    public void onTemperatureInformation(float drTemp, float psTemp, float ambientTemp, int unit) { }
    public void onTemperatureInformation(float drTemp, int drState, float psTemp, int psState,
            float ambientTemp, int ambientState, int unit) { }
    public void onClimateStatusChanged(int flag, float val1, float val2) { }
}
