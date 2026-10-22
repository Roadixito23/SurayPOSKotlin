package woyou.aidlservice.jiuiv5;

import android.graphics.Bitmap;
import woyou.aidlservice.jiuiv5.ICallback;

/**
 * Interfaz del servicio de impresión Sunmi del V1s (servicio 4.15.2), reconstruida del APK real.
 * Los 47 métodos van en el MISMO ORDEN que las transacciones del servicio (1..47): si se reordena,
 * las llamadas se cruzan. Los nombres `unknownN` son métodos cuya función no se pudo identificar por
 * análisis estático (el código está ofuscado); nunca se invocan salvo en el laboratorio de diagnóstico.
 * Ver docs/sunmi-aidl-4.15.2.md.
 */
interface IWoyouService {
    boolean unknown1(String a, in byte[] b, int c, int d);              // 1
    int unknown2();                                                      // 2  (¿estado del firmware?)
    String getServiceVersion();                                          // 3
    void printerInit(ICallback callback);                                // 4
    void printerSelfChecking(ICallback callback);                        // 5  (imprime una página de autoprueba)
    String getPrinterSerialNo();                                         // 6
    String getPrinterVersion();                                          // 7  (verificado en el V1s: devuelve «1.24»)
    String getPrinterModal();                                            // 8  (verificado en el V1s: devuelve «POS-V1S»)
    void getPrintedLength(ICallback callback);                           // 9
    void lineWrap(int n, ICallback callback);                            // 10
    void sendRAWData(in byte[] data, ICallback callback);                // 11
    void setAlignment(int alignment, ICallback callback);                // 12 (0 izq, 1 centro, 2 der)
    void setFontName(String typeface, ICallback callback);               // 13
    void setFontSize(float fontsize, ICallback callback);                // 14
    void printText(String text, ICallback callback);                     // 15
    void printTextWithFont(String text, String typeface, float fontsize, ICallback callback); // 16
    void printColumnsText(in String[] colsText, in int[] colsWidth, in int[] colsAlign, ICallback callback); // 17
    void printBitmap(in Bitmap bitmap, ICallback callback);              // 18
    void printBarCode(String data, int symbology, int height, int width, int textPosition, ICallback callback); // 19
    void printQRCode(String data, int moduleSize, int errorLevel, ICallback callback); // 20
    void printOriginalText(String text, ICallback callback);             // 21
    void unknown22(in byte[] a, ICallback callback);                     // 22 (commitPrint(TransBean[]); no se usa)
    void commitPrinterBuffer();                                          // 23
    void enterPrinterBuffer(boolean clean);                              // 24
    void exitPrinterBuffer(boolean commit);                              // 25
    void unknown26(in byte[] a, ICallback callback);                     // 26 (usa otro tipo de callback; no se usa)
    void unknown27(ICallback callback);                                  // 27 (responde onReturnString(""); no se usa)
    void unknown28();                                                    // 28
    void unknown29(ICallback callback);                                  // 29 (no responde nunca; no se usa)
    /** 30: sale del modo buffer y, con commit=true, imprime todo; avisa con onPrintResult(0, «Transaction print successful!»). Verificado en el V1s. */
    void exitPrinterBufferWithCallback(boolean commit, ICallback callback); // 30
    void printColumnsString(in String[] colsText, in int[] colsWidth, in int[] colsAlign, ICallback callback); // 31
    /** 32: estado de la impresora. Verificado en el V1s: 1 = normal, 4 = sin papel / tapa abierta (el resto, según el AIDL oficial). */
    int updatePrinterState();                                            // 32
    void printBitmapCustom(in Bitmap bitmap, int type, ICallback callback); // 33
    int unknown34();                                                     // 34
    boolean unknown35();                                                 // 35
    boolean unknown36();                                                 // 36
    boolean unknown37();                                                 // 37
    int unknown38();                                                     // 38
    int unknown39();                                                     // 39
    int unknown40();                                                     // 40
    void unknown41(String a, int b, int c, int d, ICallback callback);   // 41
    int unknown42();                                                     // 42
    void unknown43(ICallback callback);                                  // 43 (responde onRunResult(true) sin efecto visible; no se usa)
    void unknown44(int a, int b);                                        // 44
    int unknown45();                                                     // 45
    void unknown46();                                                    // 46
    void unknown47();                                                    // 47
}
