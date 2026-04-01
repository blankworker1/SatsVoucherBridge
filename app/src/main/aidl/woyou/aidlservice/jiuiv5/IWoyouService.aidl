package woyou.aidlservice.jiuiv5;

import woyou.aidlservice.jiuiv5.ICallback;

interface IWoyouService {
    void printerInit(in ICallback callback);
    void printerSelfChecking(in ICallback callback);
    String getPrinterSerialNo();
    String getPrinterVersion();
    String getServiceVersion();
    void setPrinterStyle(in int key, in int value);
    void setAlignment(in int alignment, in ICallback callback);
    void setFontName(in String typeface, in ICallback callback);
    void setFontSize(in float fontSize, in ICallback callback);
    void setFontWeight(in boolean isBold, in ICallback callback);
    void printText(in String text, in ICallback callback);
    void printTextWithFont(in String text, in String typeface, in float fontSize, in ICallback callback);
    void printColumnsText(in String[] colsTextArr, in int[] colsWidthArr, in int[] colsAlign, in ICallback callback);
    void printBitmap(in Bitmap bitmap, in ICallback callback);
    void printBarCode(in String data, in int symbology, in int height, in int width, in int textPosition, in ICallback callback);
    void printQRCode(in String data, in int moduleSize, in int errorLevel, in ICallback callback);
    void printOriginalText(in String text, in ICallback callback);
    void commitPrint(in int printCount, in ICallback callback);
    void commitPrinterBuffer();
    void enterPrinterBuffer(in boolean clean);
    void exitPrinterBuffer(in boolean commit);
    void lineWrap(in int lines, in ICallback callback);
    void cutPaper(in ICallback callback);
    int getPrinterStatus();
    void sendRAWData(in byte[] data, in ICallback callback);
}
