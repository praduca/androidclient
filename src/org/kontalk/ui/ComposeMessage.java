package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.client.ImageMessage;
import org.kontalk.client.PlainTextMessage;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;


/**
 * Conversation writing activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ComposeMessage extends FragmentActivity {
    private static final String TAG = ComposeMessage.class.getSimpleName();

    private static final int REQUEST_CONTACT_PICKER = 9721;

    /** View conversation intent action. Just provide the threadId with this. */
    public static final String ACTION_VIEW_CONVERSATION = "org.kontalk.conversation.VIEW";
    /** View conversation with userId intent action. Just provide userId with this. */
    public static final String ACTION_VIEW_USERID = "org.kontalk.conversation.VIEW_USERID";

    /** The SEND intent. */
    private Intent sendIntent;

    private ComposeMessageFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.compose_message_screen);

        // load the fragment
        mFragment = (ComposeMessageFragment) getSupportFragmentManager()
            .findFragmentById(R.id.fragment_compose_message);

        processIntent(savedInstanceState);
    }

    private void processIntent(Bundle savedInstanceState) {
        Intent intent = null;
        if (savedInstanceState != null) {
            Log.w(TAG, "restoring from saved instance");
            Uri uri = savedInstanceState.getParcelable(Uri.class.getName());
            intent = new Intent(ACTION_VIEW_CONVERSATION, uri);
        }
        else {
            intent = getIntent();
        }

        if (intent != null) {
            final String action = intent.getAction();
            Bundle args = null;

            // view intent
            // view conversation - just threadId provided
            // view conversation - just userId provided
            if (Intent.ACTION_VIEW.equals(action) ||
                    ACTION_VIEW_CONVERSATION.equals(action) ||
                    ACTION_VIEW_USERID.equals(action)) {
                args = new Bundle();
                Uri uri = intent.getData();
                Log.w(TAG, "intent uri: " + uri);

                args.putString("action", action);
                args.putParcelable("data", uri);
            }

            // send external content
            else if (Intent.ACTION_SEND.equals(action)) {
                sendIntent = intent;
                String mime = intent.getType();

                Log.i(TAG, "sending data to someone: " + mime);
                chooseContact();

                // don't do other things - onActivityResult will handle the rest
                return;
            }

            if (args != null) {
                mFragment.setMyArguments(args);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CONTACT_PICKER) {
            if (resultCode == RESULT_OK) {
                Uri rawContact = data.getData();
                if (rawContact != null) {
                    Log.i(TAG, "composing message for contact: " + rawContact);
                    Intent i = fromContactPicker(this, rawContact);
                    if (i != null) {
                        onNewIntent(i);

                        // process SEND intent if necessary
                        if (sendIntent != null)
                            processSendIntent();
                    }
                    else {
                        Toast.makeText(this, "Contact seems not to be registered on Kontalk.", Toast.LENGTH_LONG)
                            .show();
                        finish();
                    }
                }
            }
            // nothing to do - exit
            else {
                Log.w(TAG, "unknown request code " + requestCode);
                finish();
            }
        }
    }

    public static Intent fromContactPicker(Context context, Uri rawContactUri) {
        String userId = Contact.getUserId(context, rawContactUri);
        if (userId != null) {
            Conversation conv = Conversation.loadFromUserId(context, userId);
            // not found - create new
            if (conv == null) {
                Intent ni = new Intent(context, ComposeMessage.class);
                ni.setAction(ComposeMessage.ACTION_VIEW_USERID);
                ni.setData(Threads.getUri(userId));
                return ni;
            }

            return fromConversation(context, conv);
        }

        return null;
    }

    /**
     * Creates an {@link Intent} for launching the composer for a given {@link Conversation}.
     * @param context
     * @param conv
     * @return
     */
    public static Intent fromConversation(Context context, Conversation conv) {
        return fromConversation(context, conv.getThreadId());
    }

    public static Intent fromConversation(Context context, long threadId) {
        return new Intent(ComposeMessage.ACTION_VIEW_CONVERSATION,
                ContentUris.withAppendedId(Conversations.CONTENT_URI,
                        threadId),
                context, ComposeMessage.class);
    }

    private void chooseContact() {
        // TODO one day it will be like this
        // Intent i = new Intent(Intent.ACTION_PICK, Users.CONTENT_URI);
        Intent i = new Intent(this, ContactsListActivity.class);
        startActivityForResult(i, REQUEST_CONTACT_PICKER);
    }

    private void processSendIntent() {
        final String mime = sendIntent.getType();
        // send text message - just fill the text entry
        if (PlainTextMessage.supportsMimeType(mime)) {
            mFragment.setTextEntry(sendIntent.getCharSequenceExtra(Intent.EXTRA_TEXT));
        }

        else if (ImageMessage.supportsMimeType(mime)) {
            // send image immediately
            mFragment.sendImageMessage((Uri) sendIntent.getParcelableExtra(Intent.EXTRA_STREAM), mime);
        }
        else {
            Log.e(TAG, "mime " + mime + " not supported");
        }

        sendIntent = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        processIntent(null);
        mFragment.reload();
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        processIntent(state);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putParcelable(Uri.class.getName(),
                ContentUris.withAppendedId(Conversations.CONTENT_URI,
                        mFragment.getThreadId()));
        super.onSaveInstanceState(out);
    }

    public Intent getSendIntent() {
        return sendIntent;
    }

}

