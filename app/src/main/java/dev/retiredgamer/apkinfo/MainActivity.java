package dev.retiredgamer.apkinfo;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.text.format.Formatter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;

import androidx.annotation.RequiresApi;

public class MainActivity extends BaseActivity {

    private ArrayList<PackageInfo> packageInfos;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // super.onCreate() must come first: it's what creates the Window,
        // and setContentView() needs that Window to already exist.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupToolbar();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        ListView listView = findViewById(R.id.app_list_view);
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.app_name);

        PackageManager packageManager = getPackageManager();
        List<PackageInfo> installedPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS);

        packageInfos = new ArrayList<>();
        for (PackageInfo packageInfo : installedPackages) {
            if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                packageInfos.add(packageInfo);
            }
        }

        // Sort packageInfos itself (not a separate derived list) so that
        // packageInfos and the adapter's appList stay in the same order.
        // Collections.sort instead of List.sort: works down to minSdk 21.
        Collections.sort(packageInfos, (a, b) ->
                a.applicationInfo.loadLabel(packageManager).toString()
                        .compareToIgnoreCase(b.applicationInfo.loadLabel(packageManager).toString()));

        ArrayList<ApplicationInfo> appList = new ArrayList<>();
        for (PackageInfo packageInfo : packageInfos) {
            appList.add(packageInfo.applicationInfo);
        }

        listView.setAdapter(new AppListAdapter(this, appList, packageManager));
        listView.setOnItemClickListener((parent, view, position, id) ->
                showAppDetails(packageInfos.get(position)));
    }

    @SuppressLint("SetTextI18n")
    private void showAppDetails(PackageInfo packageInfo) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Light_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_app_details);

        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;

        ((ImageView) dialog.findViewById(R.id.dialog_app_icon))
                .setImageDrawable(applicationInfo.loadIcon(packageManager));
        ((TextView) dialog.findViewById(R.id.dialog_app_name))
                .setText(applicationInfo.loadLabel(packageManager));
        ((TextView) dialog.findViewById(R.id.detail_package))
                .setText("Package: " + packageInfo.packageName);

        // Fix: use getLongVersionCode() on API 28+, fallback to versionCode on older
        String versionCode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            versionCode = String.valueOf(packageInfo.getLongVersionCode());
        } else {
            versionCode = String.valueOf(packageInfo.versionCode);
        }
        ((TextView) dialog.findViewById(R.id.detail_version))
                .setText("Version: " + packageInfo.versionName + " (" + versionCode + ")");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        ((TextView) dialog.findViewById(R.id.detail_first_installed))
                .setText("First Installed: " + dateFormat.format(new Date(packageInfo.firstInstallTime)));
        ((TextView) dialog.findViewById(R.id.detail_last_updated))
                .setText("Last Updated: " + dateFormat.format(new Date(packageInfo.lastUpdateTime)));
        ((TextView) dialog.findViewById(R.id.detail_apk_path))
                .setText("External Data Folder: "
                        + Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/Android/data/" + packageInfo.packageName);

        // Main activity name
        String mainActivityName = "N/A";
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0);
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo.packageName.equals(packageInfo.packageName)) {
                mainActivityName = resolveInfo.activityInfo.name;
                break;
            }
        }
        ((TextView) dialog.findViewById(R.id.detail_activities))
                .setText("Main Activity: " + mainActivityName);
        ((TextView) dialog.findViewById(R.id.detail_uid))
                .setText("UID: " + applicationInfo.uid);
        ((TextView) dialog.findViewById(R.id.detail_target_sdk))
                .setText("Target SDK: " + applicationInfo.targetSdkVersion);

        TextView minSdkView = dialog.findViewById(R.id.detail_min_sdk);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            minSdkView.setText("Min SDK: " + applicationInfo.minSdkVersion);
        } else {
            minSdkView.setText("Min SDK: (N/A)");
        }

        // APK size
        try {
            PackageInfo fullPkg = packageManager.getPackageInfo(
                    applicationInfo.packageName, PackageManager.GET_PERMISSIONS);
            long apkSize = new File(fullPkg.applicationInfo.sourceDir).length();
            if (fullPkg.applicationInfo.splitSourceDirs != null) {
                for (String splitDir : fullPkg.applicationInfo.splitSourceDirs) {
                    apkSize += new File(splitDir).length();
                }
            }
            ((TextView) dialog.findViewById(R.id.detail_apk_size))
                    .setText("APK Size: " + Formatter.formatFileSize(this, apkSize));
        } catch (PackageManager.NameNotFoundException e) {
            ((TextView) dialog.findViewById(R.id.detail_apk_size))
                    .setText("APK Size: Unknown");
        }

        // Full storage size (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TextView fullSizeView = dialog.findViewById(R.id.detail_full_size);
            if (hasUsageStatsPermission()) {
                StorageStatsManager storageStatsManager =
                        (StorageStatsManager) getSystemService(Context.STORAGE_STATS_SERVICE);
                try {
                    ApplicationInfo freshAppInfo =
                            packageManager.getApplicationInfo(applicationInfo.packageName, 0);
                    StorageStats storageStats = storageStatsManager.queryStatsForUid(
                            freshAppInfo.storageUuid, freshAppInfo.uid);
                    long totalBytes = storageStats.getAppBytes()
                            + storageStats.getDataBytes()
                            + storageStats.getCacheBytes();
                    fullSizeView.setText("Full Size: " + Formatter.formatFileSize(this, totalBytes));
                } catch (Exception e) {
                    fullSizeView.setText("Full Size: Unknown");
                }
            } else {
                fullSizeView.setText("Full Size: Permission Needed");
                startActivity(new Intent("android.settings.USAGE_ACCESS_SETTINGS"));
            }
        }

        // Installed from (fix: use getInstallSourceInfo on API 30+)
        String installerLabel = "Unknown";
        try {
            String installerPkg;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                installerPkg = packageManager
                        .getInstallSourceInfo(packageInfo.packageName)
                        .getInstallingPackageName();
            } else {
                installerPkg = packageManager
                        .getInstallerPackageName(packageInfo.packageName);
            }
            if (installerPkg != null) installerLabel = installerPkg;
        } catch (Exception ignored) {}
        ((TextView) dialog.findViewById(R.id.detail_installer))
                .setText("Installed From: " + installerLabel);

        ((Button) dialog.findViewById(R.id.btn_view_permissions))
                .setOnClickListener(v -> showPermissionsDialog(packageInfo));
        ((Button) dialog.findViewById(R.id.close_button))
                .setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // The Dialog window itself doesn't stretch to match_parent just because
        // the root layout inside it does - Theme_Material_Light_Dialog sizes the
        // window to wrap its content, which squeezes everything into a narrow
        // column and makes long text appear cut off. Force the window width
        // after show() (must be done after show(), not before).
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    @SuppressLint("SetTextI18n")
    private void showPermissionsDialog(PackageInfo packageInfo) {
        Dialog dialog = new Dialog(this);
        dialog.setTitle("Permissions");

        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(20, 20, 20, 20);

        if (packageInfo.requestedPermissions != null) {
            ArrayList<String> permissions = new ArrayList<>(
                    Arrays.asList(packageInfo.requestedPermissions));
            Collections.sort(permissions, String.CASE_INSENSITIVE_ORDER);

            for (String permission : permissions) {
                String cleanPermission = permission.replace("android.permission.", "");

                TextView permissionView = new TextView(this);
                permissionView.setText("• " + cleanPermission);
                permissionView.setTextColor(0xFF000000);
                permissionView.setTextIsSelectable(true);
                permissionView.setPadding(10, 10, 10, 10);
                permissionView.setTextSize(16);
                permissionView.setOnLongClickListener(v -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Permission", permission));
                    Toast.makeText(getApplicationContext(),
                            "Copied: " + cleanPermission, Toast.LENGTH_SHORT).show();
                    permissionView.setBackgroundColor(0xFFE1F0DC);
                    permissionView.postDelayed(
                            () -> permissionView.setBackgroundColor(0), 1000L);
                    return true;
                });
                linearLayout.addView(permissionView);
            }
        } else {
            TextView noPermissionsView = new TextView(this);
            noPermissionsView.setText("No permissions declared.");
            noPermissionsView.setTextColor(0xFF000000);
            linearLayout.addView(noPermissionsView);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(linearLayout);
        dialog.setContentView(scrollView);
        dialog.show();
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        return appOps.checkOpNoThrow(
                "android:get_usage_stats", Process.myUid(), getPackageName())
                == AppOpsManager.MODE_ALLOWED;
    }
}
