package com.paveynganpi.snapshare.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseInstallation;
import com.parse.ParseObject;
import com.parse.ParsePush;
import com.parse.ParseQuery;
import com.parse.ParseRelation;
import com.parse.ParseUser;
import com.parse.SaveCallback;
import com.paveynganpi.snapshare.R;
import com.paveynganpi.snapshare.adapter.UserAdapter;
import com.paveynganpi.snapshare.utils.ParseConstants;

import java.util.ArrayList;
import java.util.List;


public class EditFriendsActivity extends ActionBarActivity {

    private static final String TAG = EditFriendsActivity.class.getSimpleName();
    protected List<ParseUser> mUsers;
    protected ParseRelation<ParseUser> mFriendsRelation;
    protected ParseUser mCurrentUser;
    protected GridView mGridView;
    protected ParseObject followRelation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.user_grid);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mGridView = (GridView) findViewById(R.id.friendsGrid);
        mGridView.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE);
        mGridView.setOnItemClickListener(mOnItemClickListener);

        TextView emptyTextView = (TextView) findViewById(android.R.id.empty);
        mGridView.setEmptyView(emptyTextView);

    }

    //runs each time the activity is running

    @Override
    protected void onResume() {

        super.onResume();

        mCurrentUser = ParseUser.getCurrentUser();
        mFriendsRelation = mCurrentUser.getRelation(ParseConstants.KEY_FRIENDS_RELATION);
        setSupportProgressBarIndeterminateVisibility(true);

        ParseQuery<ParseUser> query = ParseUser.getQuery();
        //query.orderByAscending(ParseConstants.KEY_USERNAME);
        query.addDescendingOrder(ParseConstants.KEY_CREATED_AT);
        query.setLimit(1000);

        query.findInBackground(new FindCallback<ParseUser>() {
            @Override
            public void done(List<ParseUser> users, ParseException e) {
                setSupportProgressBarIndeterminateVisibility(false);
                if (e == null) {
                    //success
                    mUsers = users;
                    String[] usernames = new String[mUsers.size()];
                    int i = 0;
                    for (ParseUser user : mUsers) {

                        usernames[i] = user.getUsername();
                        i++;

                    }

                    if (mGridView.getAdapter() == null) {
                        UserAdapter adapter = new UserAdapter(EditFriendsActivity.this, mUsers);
                        mGridView.setAdapter(adapter);
                    } else {
                        ((UserAdapter) mGridView.getAdapter()).refill(mUsers);
                    }
                    addFriendsCheckMark();//makes sure that the user's friends are checked even when we come back
                    //setListAdapter(adapter);

                } else {

                    Log.e(TAG, e.getMessage().toString());
                    AlertDialog.Builder builder = new AlertDialog.Builder(EditFriendsActivity.this);
                    builder.setMessage(e.getMessage());//creates a dialog with this message
                    builder.setTitle(R.string.error_title);
                    builder.setPositiveButton(android.R.string.ok, null);//creates a button to dismiss the dialog

                    AlertDialog dialog = builder.create();//create a dialog
                    dialog.show();//show the dialog

                }

            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void addFriendsCheckMark() {

        mFriendsRelation.getQuery().findInBackground(new FindCallback<ParseUser>() {
            @Override
            public void done(List<ParseUser> friends, ParseException e) {

                if (e == null) {
                    //success
                    for (int i = 0; i < mUsers.size(); i++) {

                        ParseUser user = mUsers.get(i);

                        for (ParseUser friend : friends) {

                            if (user.getObjectId().equals(friend.getObjectId())) {

                                mGridView.setItemChecked(i, true);

                            }

                        }

                    }
                } else {
                    Log.e(TAG, e.getMessage());
                }

            }
        });

    }

    protected AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            ParseUser followee = mUsers.get(position);

            ImageView checkImageView = (ImageView) view.findViewById(R.id.checkImageView);
            if (mGridView.isItemChecked(position)) {
                //add friends
                mFriendsRelation.add(mUsers.get(position));//add the friend at that position

                followRelation = new ParseObject(ParseConstants.KEY_FOLLOW_RELATION);
                followRelation.put("from", ParseUser.getCurrentUser());
                followRelation.put("to", followee);

                followRelation.saveInBackground(new SaveCallback() {
                    @Override
                    public void done(ParseException e) {
                        if(e == null){
                            Log.d("followRelation","success");
                        }
                        else{
                            Log.d("followRelation","error "+ e.getMessage());
                        }
                    }
                });
                checkImageView.setVisibility(View.VISIBLE);
                sendFolloweePushNotifications(position);

            } else {
                //remove friends
                mFriendsRelation.remove(mUsers.get(position));
                checkImageView.setVisibility(View.INVISIBLE);

            }
            mCurrentUser.saveInBackground(new SaveCallback() {//save in parse
                @Override
                public void done(ParseException e) {

                    if (e != null) {

                        Log.e(TAG, e.getMessage());

                    }

                }
            });
            followee.saveInBackground(new SaveCallback() {//save in parse
                @Override
                public void done(ParseException e) {

                    if (e != null) {

                        Log.e(TAG, e.getMessage());

                    } else {
                        Log.e("follower saving", "success saving follower to parse");
                    }

                }
            });
        }
    };

    //send push notifications to those you clicked to follow
    private void sendFolloweePushNotifications(int position) {
        ArrayList<String> followeeObjectId = new ArrayList<>();
        ParseUser followee = mUsers.get(position);
        followeeObjectId.add(followee.getObjectId());

        ParseQuery<ParseInstallation> query = ParseInstallation.getQuery();
        query.whereContainedIn(ParseConstants.KEY_USER_ID, followeeObjectId);

        ParsePush push = new ParsePush();
        push.setQuery(query);
        push.setMessage(getString(R.string.follower_push_message, ParseUser.getCurrentUser().getUsername()));
        push.sendInBackground();
    }
}