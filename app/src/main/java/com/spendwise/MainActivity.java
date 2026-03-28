package com.spendwise;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.spendwise.fragments.AccountsFragment;
import com.spendwise.fragments.AnalyticsFragment;
import com.spendwise.fragments.HomeFragment;
import com.spendwise.fragments.SettingsFragment;
import com.spendwise.fragments.TransactionsFragment;
import com.spendwise.utils.NotificationUtil;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private boolean openBudgetOnNextSettingsLoad = false;
    private static final int SMS_PERMISSION_REQUEST = 100;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationUtil.createNotificationChannel(this);

        requestSMSPermissions();

        bottomNav = findViewById(R.id.bottom_nav);

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_transactions) {
                fragment = new TransactionsFragment();
            } else if (id == R.id.nav_analytics) {
                fragment = new AnalyticsFragment();
            } else if (id == R.id.nav_accounts) {
                fragment = new AccountsFragment();
            } else if (id == R.id.nav_settings) {
                fragment = new SettingsFragment();
                if (openBudgetOnNextSettingsLoad) {
                    Bundle args = new Bundle();
                    args.putBoolean("open_budget_dialog", true);
                    fragment.setArguments(args);
                    openBudgetOnNextSettingsLoad = false;
                }
            }
            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });
    }

    private void requestSMSPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                SMS_PERMISSION_REQUEST);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void navigateTo(int navId) {
        bottomNav.setSelectedItemId(navId);
    }

    public void navigateToSettingsBudget() {
        openBudgetOnNextSettingsLoad = true;
        bottomNav.setSelectedItemId(R.id.nav_settings);
    }
}
