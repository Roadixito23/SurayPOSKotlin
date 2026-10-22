package woyou.aidlservice.jiuiv5;

/**
 * Callback del servicio de impresión Sunmi (V1s, servicio 4.15.2). `oneway`: el servicio lo invoca con
 * FLAG_ONEWAY y sin respuesta (verificado en el APK, ver docs/sunmi-aidl-4.15.2.md).
 * El ORDEN de los métodos define los códigos de transacción: no reordenar.
 */
oneway interface ICallback {
    /** 1: el comando se ejecutó (true) o falló (false). */
    void onRunResult(boolean isSuccess);
    /** 2: respuesta de texto. */
    void onReturnString(String result);
    /** 3: error. */
    void onRaiseException(int code, String msg);
    /** 4: resultado de un trabajo en modo buffer. */
    void onPrintResult(int code, String msg);
}
