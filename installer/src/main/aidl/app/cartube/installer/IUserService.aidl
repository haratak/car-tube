package app.cartube.installer;

interface IUserService {
    void destroy();
    void exit();
    String installWithPlayStore(in String apkPath);
    String fixInstallSource(in String packageName);
}
