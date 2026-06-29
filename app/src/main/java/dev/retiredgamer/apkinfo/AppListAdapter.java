package dev.retiredgamer.apkinfo;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;

public class AppListAdapter extends BaseAdapter {
    private final List<ApplicationInfo> appList;
    private final Context context;
    private final PackageManager pm;

    public AppListAdapter(Context context, List<ApplicationInfo> appList, PackageManager pm) {
        this.context = context;
        this.appList = appList;
        this.pm = pm;
    }

    @Override
    public int getCount() {
        return appList.size();
    }

    @Override
    public Object getItem(int position) {
        return appList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        boolean isNewView = (convertView == null);
        if (isNewView) {
            convertView = LayoutInflater.from(context).inflate(R.layout.app_list_item, parent, false);
        }

        ImageView icon = convertView.findViewById(R.id.app_icon);
        TextView name = convertView.findViewById(R.id.app_name);

        ApplicationInfo applicationInfo = appList.get(position);
        icon.setImageDrawable(applicationInfo.loadIcon(pm));
        name.setText(applicationInfo.loadLabel(pm).toString());
        name.setTypeface(Typeface.SANS_SERIF);

        boolean isSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        name.setTextColor(isSystemApp ? 0xFF00897B : 0xFF383838);

        // Only animate new views, not recycled ones (prevents flicker on scroll)
        if (isNewView) {
            AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
            fadeIn.setDuration(300);
            convertView.startAnimation(fadeIn);
        }

        return convertView;
    }
}
