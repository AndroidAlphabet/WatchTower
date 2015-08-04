package com.hitherejoe.proximityapidemo.android.ui.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.hitherejoe.proximityapidemo.android.ProximityApiApplication;
import com.hitherejoe.proximityapidemo.android.R;
import com.hitherejoe.proximityapidemo.android.data.BusEvent;
import com.hitherejoe.proximityapidemo.android.data.DataManager;
import com.hitherejoe.proximityapidemo.android.data.model.Attachment;
import com.hitherejoe.proximityapidemo.android.data.model.Beacon;
import com.hitherejoe.proximityapidemo.android.data.model.Namespace;
import com.hitherejoe.proximityapidemo.android.data.remote.ProximityApiService;
import com.hitherejoe.proximityapidemo.android.util.DataUtils;
import com.hitherejoe.proximityapidemo.android.util.DialogFactory;

import butterknife.ButterKnife;
import butterknife.InjectView;
import retrofit.RetrofitError;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public class AddAttachmentActivity extends BaseActivity {

    @InjectView(R.id.spinner_namespace)
    Spinner mNamespaceSpinner;

    @InjectView(R.id.edit_text_data)
    EditText mAttachmentDataText;

    @InjectView(R.id.text_data_error_message)
    TextView mDataErrorText;

    private static final String TAG = "RegisterActivity";
    private DataManager mDataManager;
    private CompositeSubscription mSubscriptions;
    private static final String EXTRA_BEACON = "com.hitherejoe.proximityapidemo.android.ui.activity.UpdateActivity.EXTRA_BEACON";
    private Beacon mBeacon;
    private ProgressDialog mProgressDialog;
    ArrayAdapter<String> mSpinnerAdapter;

    public static Intent getStartIntent(Context context, Beacon beacon) {
        Intent intent = new Intent(context, AddAttachmentActivity.class);
        intent.putExtra(EXTRA_BEACON, beacon);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_attachment);
        ButterKnife.inject(this);
        mBeacon = getIntent().getParcelableExtra(EXTRA_BEACON);
        if (mBeacon == null) throw new IllegalArgumentException("Beacon is required!");
        mSubscriptions = new CompositeSubscription();
        mDataManager = ProximityApiApplication.get().getDataManager();
        mSpinnerAdapter = new ArrayAdapter<>(AddAttachmentActivity.this, android.R.layout.simple_spinner_item);
        mNamespaceSpinner.setAdapter(mSpinnerAdapter);
        setupActionBar();
        retrieveNamespaces();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSubscriptions.unsubscribe();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.add_attachment, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_done:
                validateAttachmentData();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void validateAttachmentData() {
        String data = mAttachmentDataText.getText().toString();
        mDataErrorText.setVisibility(data.length() == 0 ? View.VISIBLE : View.GONE);
        if (data.length() > 0) {
            Attachment attachment = new Attachment();
            attachment.data = DataUtils.base64Encode(DataUtils.base64Decode(data.replace(" ", "")));
            attachment.namespacedType = mNamespaceSpinner.getSelectedItem() + "/text";
            addAttachment(attachment);
        }
    }

    private void retrieveNamespaces() {
        mProgressDialog = DialogFactory.createProgressDialog(this, R.string.progress_dialog_retrieving_namespaces);
        mProgressDialog.show();
        mSubscriptions.add(mDataManager.getNamespaces()
                .subscribeOn(mDataManager.getScheduler())
                .subscribe(new Subscriber<ProximityApiService.NamespacesResponse>() {
                    @Override
                    public void onCompleted() {
                        mProgressDialog.dismiss();
                    }

                    @Override
                    public void onError(Throwable e) {
                        mProgressDialog.dismiss();
                        Log.e(TAG, "There was an error retrieving the namespaces " + e);
                    }

                    @Override
                    public void onNext(ProximityApiService.NamespacesResponse namespacesResponse) {
                        for (Namespace namespace : namespacesResponse.namespaces) {
                            mSpinnerAdapter.add(namespace.namespaceName);
                        }
                    }
                }));
    }

    private void addAttachment(Attachment attachment) {
        mSubscriptions.add(mDataManager.createAttachment(mBeacon.beaconName, attachment)
                .subscribeOn(mDataManager.getScheduler())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Attachment>() {
                    @Override
                    public void onCompleted() {
                        ProximityApiApplication.get().getBus().post(new BusEvent.AttachmentAdded());
                        finish();
                    }

                    @Override
                    public void onError(Throwable error) {
                        Log.e(TAG, "There was a problem adding the attachment " + error);
                        if (error instanceof RetrofitError) {
                            DialogFactory.createRetrofitErrorDialog(AddAttachmentActivity.this, (RetrofitError) error).show();
                        } else {
                            DialogFactory.createSimpleOkErrorDialog(AddAttachmentActivity.this, getString(R.string.dialog_error_title), getString(R.string.dialog_general_error_Message)).show();
                        }
                    }

                    @Override
                    public void onNext(Attachment attachment) { }
                }));
    }

}