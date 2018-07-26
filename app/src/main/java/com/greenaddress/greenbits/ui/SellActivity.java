package com.greenaddress.greenbits.ui;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.View;

import de.schildbach.wallet.ui.ScanActivity;


public class SellActivity extends GaActivity {
    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {
        setContentView(R.layout.activity_sell);

        if (mService.isElements())
            setTitle(R.string.cash_in);

        final Intent intent = getIntent();
        final boolean isBitcoinUri = TabbedMainActivity.isBitcoinScheme(intent) ||
                intent.hasCategory(Intent.CATEGORY_BROWSABLE) ||
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction());
        final View v = UI.find(this, R.id.sell_fragment);
        if (isBitcoinUri) {
            v.setTag(R.id.tag_bitcoin_uri, getIntent().getData());
        }
        if (intent.getStringExtra("sendAmount") != null) {
            v.setTag(R.id.tag_amount, intent.getStringExtra("sendAmount"));
        }

        final SendFragment sellFragment = new SendFragment();
        sellFragment.setIsExchanger(true);
        sellFragment.setPageSelected(true);
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.sell_fragment, sellFragment, "tag2")
                .disallowAddToBackStack()
                .commit();
    }

    @Override
    public void onResumeWithService() {
        if (mService.isForcedOff()) {
            // FIXME: Should pass flag to activity so it shows it was forced logged out
            startActivity(new Intent(this, FirstScreenActivity.class));
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] granted) {
       if (requestCode == 100 &&
           isPermissionGranted(granted, R.string.err_qrscan_requires_camera_permissions))
            startActivityForResult(new Intent(this, ScanActivity.class),
                                   TabbedMainActivity.REQUEST_SEND_QR_SCAN_EXCHANGER);
    }
}
