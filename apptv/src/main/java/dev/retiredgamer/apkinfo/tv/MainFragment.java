package dev.retiredgamer.apkinfo.tv;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainFragment extends BrowseSupportFragment {

    private PackageManager packageManager;
    private final ArrayList<PackageInfo> packageInfos = new ArrayList<>();

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        packageManager = requireActivity().getPackageManager();

        setTitle("APK Info TV");
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        loadApplications();
        setupClickListener();
    }

    private void loadApplications() {
        List<PackageInfo> installedPackages =
                packageManager.getInstalledPackages(
                        PackageManager.GET_PERMISSIONS
                );

        packageInfos.clear();

        for (PackageInfo packageInfo : installedPackages) {
            if (packageInfo.applicationInfo == null) {
                continue;
            }

            ApplicationInfo appInfo = packageInfo.applicationInfo;

            // Показываем пользовательские приложения, а также системные,
            // которые были обновлены (на TV-приставках многие обычные
            // программы всё ещё помечены FLAG_SYSTEM прошивкой).
            boolean isSystem =
                    (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

            boolean wasUpdated =
                    (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

            if (!isSystem || wasUpdated) {
                packageInfos.add(packageInfo);
            }
        }

        Collections.sort(
                packageInfos,
                (a, b) -> a.applicationInfo
                        .loadLabel(packageManager)
                        .toString()
                        .compareToIgnoreCase(
                                b.applicationInfo
                                        .loadLabel(packageManager)
                                        .toString()
                        )
        );

        AppPresenter presenter = new AppPresenter();

        ArrayObjectAdapter appAdapter =
                new ArrayObjectAdapter(presenter);

        for (PackageInfo packageInfo : packageInfos) {
            appAdapter.add(packageInfo);
        }

        ArrayObjectAdapter rowsAdapter =
                new ArrayObjectAdapter(new ListRowPresenter());

        HeaderItem header =
                new HeaderItem(0, "INSTALLED APPLICATIONS");

        rowsAdapter.add(
                new ListRow(header, appAdapter)
        );

        setAdapter(rowsAdapter);
    }

    private void setupClickListener() {
        setOnItemViewClickedListener(
                new OnItemViewClickedListener() {
                    @Override
                    public void onItemClicked(
                            Presenter.ViewHolder itemViewHolder,
                            Object item,
                            RowPresenter.ViewHolder rowViewHolder,
                            Row row) {

                        if (item instanceof PackageInfo) {
                            showAppDetails((PackageInfo) item);
                        }
                    }
                }
        );
    }

    @SuppressLint("SetTextI18n")
    private void showAppDetails(PackageInfo packageInfo) {

        ApplicationInfo applicationInfo =
                packageInfo.applicationInfo;

        String appName =
                applicationInfo
                        .loadLabel(packageManager)
                        .toString();

        String versionCode;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            versionCode =
                    String.valueOf(
                            packageInfo.getLongVersionCode()
                    );
        } else {
            versionCode =
                    String.valueOf(packageInfo.versionCode);
        }

        String versionName =
                packageInfo.versionName != null
                        ? packageInfo.versionName
                        : "Unknown";

        SimpleDateFormat dateFormat =
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()
                );

        String firstInstalled =
                dateFormat.format(
                        new Date(packageInfo.firstInstallTime)
                );

        String lastUpdated =
                dateFormat.format(
                        new Date(packageInfo.lastUpdateTime)
                );

        long apkSize =
                getApkSize(applicationInfo);

        String installer =
                getInstaller(packageInfo.packageName);

        String mainActivity =
                getMainActivity(packageInfo.packageName);

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_app_details, null);

        ((ImageView) view.findViewById(R.id.dialog_app_icon))
                .setImageDrawable(applicationInfo.loadIcon(packageManager));
        ((TextView) view.findViewById(R.id.dialog_app_name))
                .setText(appName);
        ((TextView) view.findViewById(R.id.detail_package))
                .setText("Package: " + packageInfo.packageName);
        ((TextView) view.findViewById(R.id.detail_version))
                .setText("Version: " + versionName + " (" + versionCode + ")");
        ((TextView) view.findViewById(R.id.detail_first_installed))
                .setText("First Installed: " + firstInstalled);
        ((TextView) view.findViewById(R.id.detail_last_updated))
                .setText("Last Updated: " + lastUpdated);
        ((TextView) view.findViewById(R.id.detail_apk_path))
                .setText("APK Path: " + applicationInfo.sourceDir);
        ((TextView) view.findViewById(R.id.detail_activities))
                .setText("Main Activity: " + mainActivity);
        ((TextView) view.findViewById(R.id.detail_uid))
                .setText("UID: " + applicationInfo.uid);
        ((TextView) view.findViewById(R.id.detail_target_sdk))
                .setText("Target SDK: " + applicationInfo.targetSdkVersion);

        TextView minSdkView = view.findViewById(R.id.detail_min_sdk);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            minSdkView.setText("Min SDK: " + applicationInfo.minSdkVersion);
        } else {
            minSdkView.setText("Min SDK: (N/A)");
        }

        ((TextView) view.findViewById(R.id.detail_apk_size))
                .setText("APK Size: " + Formatter.formatFileSize(requireContext(), apkSize));
        ((TextView) view.findViewById(R.id.detail_installer))
                .setText("Installed From: " + installer);

        AlertDialog dialog =
                new AlertDialog.Builder(requireContext())
                        .setView(view)
                        .create();

        ((Button) view.findViewById(R.id.btn_view_permissions))
                .setOnClickListener(v -> showPermissions(packageInfo));
        ((Button) view.findViewById(R.id.close_button))
                .setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private String getMainActivity(String packageName) {

        String activityName = "N/A";

        try {
            android.content.Intent intent =
                    new android.content.Intent(
                            android.content.Intent.ACTION_MAIN
                    );

            intent.addCategory(
                    android.content.Intent.CATEGORY_LAUNCHER
            );

            List<android.content.pm.ResolveInfo> activities =
                    packageManager.queryIntentActivities(
                            intent,
                            0
                    );

            for (android.content.pm.ResolveInfo info : activities) {

                if (info.activityInfo != null &&
                        packageName.equals(
                                info.activityInfo.packageName
                        )) {

                    activityName =
                            info.activityInfo.name;

                    break;
                }
            }

        } catch (Exception ignored) {
        }

        return activityName;
    }

    private long getApkSize(
            ApplicationInfo applicationInfo) {

        long apkSize = 0;

        try {
            if (applicationInfo.sourceDir != null) {
                apkSize +=
                        new File(
                                applicationInfo.sourceDir
                        ).length();
            }

            if (applicationInfo.splitSourceDirs != null) {

                for (String splitDir :
                        applicationInfo.splitSourceDirs) {

                    apkSize +=
                            new File(splitDir).length();
                }
            }

        } catch (Exception ignored) {
        }

        return apkSize;
    }

    private String getInstaller(String packageName) {

        String installer = "Unknown";

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.R) {

                String installerPackage =
                        packageManager
                                .getInstallSourceInfo(
                                        packageName
                                )
                                .getInstallingPackageName();

                if (installerPackage != null) {
                    installer = installerPackage;
                }

            } else {

                String installerPackage =
                        packageManager
                                .getInstallerPackageName(
                                        packageName
                                );

                if (installerPackage != null) {
                    installer = installerPackage;
                }
            }

        } catch (Exception ignored) {
        }

        return installer;
    }

    @SuppressLint("SetTextI18n")
    private void showPermissions(
            PackageInfo packageInfo) {

        LinearLayout layout =
                new LinearLayout(requireContext());

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        layout.setPadding(
                30,
                20,
                30,
                20
        );

        StringBuilder clipboardText =
                new StringBuilder("Permissions:\n");

        if (packageInfo.requestedPermissions != null &&
                packageInfo.requestedPermissions.length > 0) {

            ArrayList<String> permissions =
                    new ArrayList<>();

            Collections.addAll(
                    permissions,
                    packageInfo.requestedPermissions
            );

            Collections.sort(
                    permissions,
                    String.CASE_INSENSITIVE_ORDER
            );

            for (String permission :
                    permissions) {

                TextView permissionView =
                        new TextView(
                                requireContext()
                        );

                String cleanPermission =
                        permission.replace(
                                "android.permission.",
                                ""
                        );

                permissionView.setText(
                        "• " + cleanPermission
                );

                permissionView.setTextColor(
                        Color.WHITE
                );

                permissionView.setTextSize(
                        18
                );

                permissionView.setBackgroundResource(
                        R.drawable.detail_border
                );

                int paddingPx =
                        (int) (14 * getResources()
                                .getDisplayMetrics().density);

                permissionView.setPadding(
                        paddingPx,
                        paddingPx,
                        paddingPx,
                        paddingPx
                );

                LinearLayout.LayoutParams rowParams =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );

                rowParams.bottomMargin =
                        (int) (10 * getResources()
                                .getDisplayMetrics().density);

                permissionView.setLayoutParams(rowParams);

                permissionView.setFocusable(true);
                permissionView.setFocusableInTouchMode(true);
                permissionView.setTextIsSelectable(true);

                layout.addView(
                        permissionView
                );

                clipboardText.append("• ")
                        .append(cleanPermission)
                        .append("\n");
            }

        } else {

            TextView noPermissions =
                    new TextView(
                            requireContext()
                    );

            noPermissions.setText(
                    "No permissions declared."
            );

            noPermissions.setTextColor(
                    Color.WHITE
            );

            noPermissions.setTextSize(
                    18
            );

            layout.addView(
                    noPermissions
            );

            clipboardText.append("No permissions declared.");
        }

        ScrollView scrollView =
                new ScrollView(
                        requireContext()
                );

        scrollView.addView(layout);

        AlertDialog dialog =
                new AlertDialog.Builder(
                        requireContext()
                )
                        .setTitle("Permissions")
                        .setView(scrollView)
                        .setPositiveButton(
                                "Copy All",
                                (d, which) -> {

                                    android.content.ClipboardManager clipboard =
                                            (android.content.ClipboardManager)
                                                    requireContext().getSystemService(
                                                            android.content.Context.CLIPBOARD_SERVICE
                                                    );

                                    if (clipboard != null) {

                                        clipboard.setPrimaryClip(
                                                android.content.ClipData.newPlainText(
                                                        "Permissions",
                                                        clipboardText.toString()
                                                )
                                        );

                                        android.widget.Toast.makeText(
                                                requireContext(),
                                                "Copied to clipboard",
                                                android.widget.Toast.LENGTH_SHORT
                                        ).show();
                                    }
                                }
                        )
                        .setNegativeButton(
                                "Close",
                                null
                        )
                        .create();

        dialog.show();

        TextView message =
                dialog.findViewById(
                        android.R.id.message
                );

        if (message != null) {
            message.setTextSize(18);
        }
    }

    private class AppPresenter
            extends Presenter {

        private static final int COLOR_DEFAULT =
                android.graphics.Color.rgb(35, 35, 35);
        private static final int COLOR_SELECTED =
                android.graphics.Color.rgb(0, 137, 123);

        @Override
        public ViewHolder onCreateViewHolder(
                ViewGroup parent) {

            ImageCardView cardView =
                    new ImageCardView(parent.getContext()) {
                        @Override
                        public void setSelected(boolean selected) {
                            updateCardBackground(this, selected);
                            super.setSelected(selected);
                        }
                    };

            cardView.setFocusable(true);
            cardView.setFocusableInTouchMode(true);

            cardView.setMainImageDimensions(
                    180,
                    180
            );

            updateCardBackground(cardView, false);

            return new ViewHolder(
                    cardView
            );
        }

        private void updateCardBackground(
                ImageCardView cardView,
                boolean selected) {

            int color = selected ? COLOR_SELECTED : COLOR_DEFAULT;

            cardView.setInfoAreaBackgroundColor(color);
            cardView.setBackgroundColor(color);
        }

        @Override
        public void onBindViewHolder(
                ViewHolder viewHolder,
                Object item) {

            PackageInfo packageInfo =
                    (PackageInfo) item;

            ApplicationInfo applicationInfo =
                    packageInfo.applicationInfo;

            ImageCardView cardView =
                    (ImageCardView)
                            viewHolder.view;

            String name =
                    applicationInfo
                            .loadLabel(
                                    packageManager
                            )
                            .toString();

            cardView.setTitleText(name);

            cardView.setMainImage(
                    applicationInfo.loadIcon(
                            packageManager
                    )
            );
        }

        @Override
        public void onUnbindViewHolder(
                ViewHolder viewHolder) {
        }
    }
}