package com.greenaddress.greenbits.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class LoginActivity extends GaActivity {

    protected void onLoginSuccess() {
        // After login succeeds, show system messaages if there are any
        final Intent intent;
        if (mService.isWatchOnly() || !mService.haveUnackedMessages())
            intent = new Intent(LoginActivity.this, TabbedMainActivity.class);
        else
            intent = new Intent(LoginActivity.this, MessagesActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishOnUiThread();
    }

    @Override
    protected void onResumeWithService() {
        if (mService.isLoggedOrLoggingIn()) {
            // already logged in, could be from different app via intent
            onLoginSuccess();
        }
    }

    protected void checkPinExist(final boolean fromPinActivity) {
        final String ident = mService.getPinPref().getString("ident", null);

        if (fromPinActivity && ident == null) {
            startActivity(new Intent(this, FirstScreenActivity.class));
            finish();
        }
        if (!fromPinActivity && ident != null) {
            startActivity(new Intent(this, PinActivity.class));
            finish();
        }
    }

    protected void chooseNetworkIfMany(final boolean fromPinActivity) {
        final Set<String> networkSelector = mService.cfg().getStringSet("network_selector", new HashSet<>());
        if (networkSelector.size()>1) {
            final Set<String> networkSelectorSet = mService.cfg().getStringSet("network_selector", new HashSet<>());
            final List<String> networkSelectorList = new ArrayList<>(networkSelectorSet);
            Collections.sort(networkSelectorList);

            final MaterialDialog materialDialog = UI.popup(this, R.string.select_network, R.string.choose, R.string.choose_and_default)
                    .items(networkSelectorList)
                    .itemsCallbackSingleChoice(0, (dialog, v, which, text) -> {
                        selectedNetwork(text.toString(), false);
                        checkPinExist(fromPinActivity);
                        return true;
                    })
                    .onNegative((dialog, which) -> {
                        selectedNetwork(networkSelectorList.get(dialog.getSelectedIndex()), true);
                        checkPinExist(fromPinActivity);
                    })
                    .build();

            materialDialog.show();
        }
    }

    protected void selectedNetwork(String which, boolean makeDefault) {
        Log.i("TAG", "which " + which + " default:" + makeDefault);
        final SharedPreferences.Editor editor = mService.cfg().edit();
        if (makeDefault) {
            Set<String> networkSelectorNew = new HashSet<>();
            networkSelectorNew.add(which);
            editor.putStringSet("network_selector", networkSelectorNew);
        }
        editor.putString("network_selected", which);
        editor.apply();
        mService.updateSelectedNetwork();
    }

}
