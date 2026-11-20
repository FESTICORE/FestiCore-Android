# 릴리스에서 verbose · debug · info 로그 호출을 제거한다. (우리 로거는 Log.println을 쓰고 NOTICE 미만을 이미 막는다.)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
