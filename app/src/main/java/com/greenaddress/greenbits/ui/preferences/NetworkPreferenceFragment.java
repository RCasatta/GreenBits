package com.greenaddress.greenbits.ui.preferences;


import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.util.Log;
import android.webkit.URLUtil;
import android.widget.Toast;

import com.google.common.base.Joiner;
import com.greenaddress.greenapi.Network2;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.UI;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.schildbach.wallet.ui.ScanActivity;

/**
 * This fragment shows general preferences only. It is used when the
 * activity is showing a two-pane settings UI.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class NetworkPreferenceFragment extends GAPreferenceFragment {
    private static final String TAG = GAPreferenceFragment.class.getSimpleName();
    private static final int QRSCANNER = 1338;
    private static final int CAMERA_PERMISSION = 150;

    private ListPreference mNetworkCustomRemove;
    private MultiSelectListPreference mNetworkSelector;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mService == null) {
            Log.d(TAG, "Avoiding create on null service");
            return;
        }

        addPreferencesFromResource(R.xml.pref_network);
        setHasOptionsMenu(true);

        final Preference host = find("proxy_host");
        host.setOnPreferenceChangeListener(mListener);
        host.setSummary(mService.getProxyHost());
        final Preference port = find("proxy_port");
        port.setSummary(mService.getProxyPort());
        port.setOnPreferenceChangeListener(mListener);
        final Preference torEnabled  = find("tor_enabled");
        if (mService.getNetwork().getGaitOnion() == null)
            torEnabled.setEnabled(false);
        else {
            torEnabled.setSummary(getString(R.string.torSummary, mService.getNetwork().getGaitOnion()));
            torEnabled.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object o) {
                    if (mService != null)
                        mService.disconnect(true);
                    return true;
                }
            });
        }

        final Set<String> customNetworks = mService.cfg().getStringSet("custom_networks", new HashSet<>());

        // Network selector
        mNetworkSelector = find("network_selector");
        mNetworkSelector.setOnPreferenceChangeListener((preference, selectedPreferencesObject) -> {
            final Set<String> selectedPreferences = (Set<String>) selectedPreferencesObject;
            if (selectedPreferences.isEmpty()) {
                UI.toast(getActivity(), "Cannot select any network. selecting Bitcoin by default", Toast.LENGTH_LONG);
                selectedPreferences.add("Bitcoin");
                mService.cfg().edit().putStringSet("network_selector", selectedPreferences).apply();
            }
            mNetworkSelector.setSummary( Joiner.on(", ").join(selectedPreferences) );

            return true;
        });
        final Set<String> selectedPreferences = mService.cfg().getStringSet("network_selector", new HashSet<>());
        mNetworkSelector.setSummary( Joiner.on(", ").join(selectedPreferences) );

        mNetworkCustomRemove = find("custom_networks_remove");
        final String[] entries = customNetworks.toArray(new String[customNetworks.size()]);
        mNetworkCustomRemove.setEntries(entries);
        mNetworkCustomRemove.setEntryValues(entries);
        mNetworkCustomRemove.setOnPreferenceChangeListener((preference, newValue) -> {
            removeNetwork(newValue.toString());
            return true;
        });

        final EditTextPreference networkCustomAdd = find("custom_networks_add");
        networkCustomAdd.setOnPreferenceChangeListener((preference, newValue) -> {
            if (URLUtil.isValidUrl(newValue.toString())) {
                addNetworkFromUrl(newValue.toString());
            } else {
                UI.toast(getActivity(), "Not a valid URL", Toast.LENGTH_LONG);
            }
            return true;
        });

        final Preference networkCustomAddQr = find("custom_networks_add_qr");
        networkCustomAddQr.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                onScanClicked();
                return true;
            }
        });

        syncCustomNetworks(customNetworks);
    }

    private void syncCustomNetworks(final Set<String> customNetworksNew) {
        mService.cfg().edit()
                .putStringSet("custom_networks", customNetworksNew)
                .apply();
        final List<String> customNetworksNewList = new ArrayList<>(customNetworksNew);
        Collections.sort(customNetworksNewList);
        final String[] entriesNow = customNetworksNewList.toArray(new String[customNetworksNewList.size()]);
        mNetworkCustomRemove.setEntries(entriesNow);
        mNetworkCustomRemove.setEntryValues(entriesNow);

        final String[] standardNetworks = getResources().getStringArray(R.array.available_networks);
        final List<String> standardAndCustomNetworks = new ArrayList<>();
        standardAndCustomNetworks.addAll( Arrays.asList(standardNetworks) );
        standardAndCustomNetworks.addAll( customNetworksNewList );

        final String[] standardAndCustomNetworksArray = standardAndCustomNetworks.toArray(new String[standardAndCustomNetworks.size()]);
        mNetworkSelector.setEntries(standardAndCustomNetworksArray);
        mNetworkSelector.setEntryValues(standardAndCustomNetworksArray);
    }

    private final Preference.OnPreferenceChangeListener mListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, final Object o) {
            preference.setSummary(o.toString());
            if (mService != null)
                mService.disconnect(true);
            return true;
        }
    };

    private void addNetworkFromUrl(final String url){
        final OkHttpClient httpClient = new OkHttpClient();
        final Request request = new Request.Builder().url(url)
                .addHeader("Accept", "application/json").build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(final Request request, final IOException e) {
                UI.toast(getActivity(), "Not a valid URL", Toast.LENGTH_LONG);
            }

            @Override
            public void onResponse(final Response response) {
                try {
                    addNetwork(response.body().string());
                } catch (final Exception e) {
                    UI.toast(getActivity(), "Not a valid Network config", Toast.LENGTH_LONG);
                }
            }
        });
    }

    private void addNetwork(final String networkConfig) {
        final Network2 network = Network2.from(networkConfig);
        final Set<String> customNetworksNew = mService.cfg().getStringSet("custom_networks", new HashSet<>());
        if (customNetworksNew.contains(network.getName().toString())) {
            UI.toast(getActivity(), "Custom Network already present", Toast.LENGTH_LONG);
            return;
        }
        customNetworksNew.add(network.getName().toString());
        UI.toast(getActivity(), "Custom Network added, enable it in the Network selector chooser", Toast.LENGTH_LONG);
        syncCustomNetworks(customNetworksNew);

        mService.cfg().edit()
                .putString("network_" + network.getName(), networkConfig)
                .apply();

    }

    private void removeNetwork(final String networkName) {
        final Set<String> customNetworksNew = mService.cfg().getStringSet("custom_networks", new HashSet<>());
        if (!customNetworksNew.contains(networkName)) {
            UI.toast(getActivity(), "Custom Network not found", Toast.LENGTH_LONG);
            return;
        }
        final Set<String> networkSelectorPreferences = mService.cfg().getStringSet("network_selector", new HashSet<>());
        if (networkSelectorPreferences.contains(networkName)) {
            UI.toast(getActivity(), "Deselect Custom Network before remove", Toast.LENGTH_LONG);
            return;
        }
        customNetworksNew.remove(networkName);
        networkSelectorPreferences.remove(networkName);
        syncCustomNetworks(customNetworksNew);

        mService.cfg().edit()
                .remove("network_" + networkName)
                .apply();
    }

    private void onScanClicked() {
        final String[] perms = { "android.permission.CAMERA" };
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1 &&
                getActivity().checkSelfPermission(perms[0]) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(perms, CAMERA_PERMISSION);
        else {
            final Intent scanner = new Intent(getActivity(), ScanActivity.class);
            startActivityForResult(scanner, QRSCANNER);
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case QRSCANNER:
                if (data != null && data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT) != null) {
                    final String networkConfig = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
                    addNetwork(networkConfig);
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] granted) {
        if (requestCode == CAMERA_PERMISSION &&
                isPermissionGranted(granted, R.string.err_qrscan_requires_camera_permissions))
            startActivityForResult(new Intent(getActivity(), ScanActivity.class), QRSCANNER);
    }

    protected boolean isPermissionGranted(final int[] granted, final int msgId) {
        if (granted == null || granted.length == 0 || granted[0] != PackageManager.PERMISSION_GRANTED) {
            UI.toast(getActivity(), msgId, Toast.LENGTH_SHORT);
            return false;
        }
        return true;
    }
}
