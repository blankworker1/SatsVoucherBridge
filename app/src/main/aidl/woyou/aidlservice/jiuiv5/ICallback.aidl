package woyou.aidlservice.jiuiv5;

interface ICallback {
    void onRunResult(in boolean isSuccess);
    void onReturnString(in String result);
    void onRaiseException(in int code, in String msg);
    void onPrintResult(in int code, in String msg);
}
