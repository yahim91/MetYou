package com.metyou.fragments.friends;


import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.DateTime;
import com.metyou.MainActivity;
import com.metyou.R;
import com.metyou.cloud.services.model.SocialIdentity;
import com.metyou.cloud.services.model.UserEncountered;
import com.metyou.cloud.services.model.UsersBatch;
import com.metyou.cloud.services.model.UsersRequest;
import com.metyou.cloudapi.CloudApi;
import com.metyou.cloudapi.GetUsersTask;
import com.metyou.fragments.friends.refresher.CustomSpinner;
import com.metyou.social.SocialProvider;
import com.metyou.util.ImageCache;
import com.metyou.util.ImageFetcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;

public class BuddiesFragment extends Fragment implements GetUsersTask.GetUsersCallback,
        EndlessScrollListener.EndlessScrollCallback {

    private static final String TAG = "BuddiesFragment";
    private static final int AMOUNT_TO_LOAD = 10;
    private ListView userListView;
    private UserAdapter arrayAdapter;
    private ArrayList<ListRow> userList;
    private static LoaderRow loaderRow = new LoaderRow();
    private boolean loaderSet = false;

    private SwipeRefreshLayout swipeRefreshLayout;
    private Date lastRefreshDate;
    private Comparator<ListRow> mComparator;
    private ImageFetcher imageFetcher;
    private EndlessScrollListener endlessScrollListener;
    private Button refreshButton;
    private CustomSpinner customSpinner;
    private TextView emptyText;
    private String cursorStart;
    private String cursorTop;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userList = new ArrayList<ListRow>();

        //sort data desc with respect to last seen date
        mComparator = new Comparator<ListRow>() {
            @Override
            public int compare(ListRow lhs, ListRow rhs) {
                if (lhs instanceof LoaderRow) {
                    return 1;
                } else if (rhs instanceof LoaderRow) {
                    return -1;
                } else {
                    UserRow luser = (UserRow)lhs;
                    UserRow ruser = (UserRow)rhs;
                    return 0 - luser.getLastSeen().compareTo(ruser.getLastSeen());
                }
            }
        };
        imageFetcher = ((MainActivity)getActivity()).getImageFetcher();

        arrayAdapter = new UserAdapter(getActivity(),
                android.R.layout.simple_list_item_1,
                userList,
                imageFetcher);
        endlessScrollListener = new EndlessScrollListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.buddies_fragment, container, false);

        swipeRefreshLayout = (SwipeRefreshLayout)view.findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_dark,
                android.R.color.holo_green_dark,
                android.R.color.holo_blue_dark,
                android.R.color.holo_green_dark
        );

        swipeRefreshLayout.setEnabled(false);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.d(TAG, "refresh");
                lastRefreshDate = new Date();
                UsersRequest usersRequest = new UsersRequest();
                usersRequest.setUserKey(SocialProvider.getId());
                usersRequest.setBeginningDate(new DateTime(lastRefreshDate));
                usersRequest.setCursorTop(cursorTop);
                usersRequest.setCount(0);
                usersRequest.setOffset(0);
                requestUsers(usersRequest, GetUsersTask.RequestType.REFRESH);
            }
        });
        userListView = (ListView)view.findViewById(R.id.buddy_list);
        userListView.setOnScrollListener(endlessScrollListener);
        View emptyView = view.findViewById(android.R.id.empty);
        refreshButton = (Button)emptyView.findViewById(R.id.refresh_btn);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        customSpinner = (CustomSpinner) emptyView.findViewById(R.id.spinner);
        emptyText = (TextView) emptyView.findViewById(R.id.empty_text);

        userListView.setEmptyView(emptyView);
        userListView.setAdapter(arrayAdapter);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (arrayAdapter.isEmpty()) {
            refresh();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBottom() {
        Log.d(TAG, "on Bottom");
        UsersRequest usersRequest = new UsersRequest();
        usersRequest.setUserKey(SocialProvider.getId());
        usersRequest.setBeginningDate(new DateTime(lastRefreshDate));
        usersRequest.setCount(AMOUNT_TO_LOAD);
        usersRequest.setOffset(arrayAdapter.getCount() - 2); //count the load row
        usersRequest.setCursorStart(cursorStart);
        requestUsers(usersRequest, GetUsersTask.RequestType.MORE);
    }

    @Override
    public void onUsersLoaded(GetUsersTask.RequestType reqType, UsersBatch usersBatch) {
        if (usersBatch == null || usersBatch.getUsers() == null) {
            handleRequestType(reqType, true);
            return;
        }
        arrayAdapter.setNotifyOnChange(false);
        Log.d(TAG, usersBatch.getUsers().toString());

        for (UserEncountered ue : usersBatch.getUsers()) {
            UserRow userRow = new UserRow(ue);
            if (arrayAdapter.contains(userRow)) {
                Log.d(TAG, "found item " + userRow.getKey());
                arrayAdapter.update(userRow);
            } else {
                arrayAdapter.add(userRow);
            }
        }

        handleRequestType(reqType, usersBatch.getReachedEnd());
        arrayAdapter.sort(mComparator);
        arrayAdapter.notifyDataSetChanged();
        if (usersBatch.getCursorTop() != null) {
            cursorTop = usersBatch.getCursorTop();
        }
        cursorStart = usersBatch.getCursorStart();
    }

    private void handleRequestType(GetUsersTask.RequestType reqType, boolean reachedEnd) {
        if (reqType == GetUsersTask.RequestType.MORE) {
            if (!reachedEnd && !loaderSet) {
                loaderSet = true;
                arrayAdapter.add(loaderRow);
            } else if(reachedEnd && loaderSet) {
                arrayAdapter.remove(loaderRow);
                loaderSet = false;
                endlessScrollListener.performTaskOnBottom(false);
            }
            endlessScrollListener.onBottomTaskFinished();
        } else if (reqType == GetUsersTask.RequestType.REFRESH) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                }
            });
        } else {
            if (arrayAdapter.getCount() > 0) {
                swipeRefreshLayout.setEnabled(true);
            } else {
                customSpinner.setVisibility(View.INVISIBLE);
                refreshButton.setVisibility(View.VISIBLE);
                emptyText.setVisibility(View.VISIBLE);
            }
            if (!reachedEnd && !loaderSet) {
                loaderSet = true;
                arrayAdapter.add(loaderRow);
            } else if (reachedEnd) {
                endlessScrollListener.performTaskOnBottom(false);
            }
        }
    }

    public void refresh() {
        lastRefreshDate = new Date();
        UsersRequest usersRequest = new UsersRequest();
        usersRequest.setUserKey(SocialProvider.getId());
        usersRequest.setBeginningDate(new DateTime(lastRefreshDate));
        usersRequest.setCount(AMOUNT_TO_LOAD);
        usersRequest.setOffset(0);
        requestUsers(usersRequest, GetUsersTask.RequestType.EMPTY);
        refreshButton.setVisibility(View.INVISIBLE);
        customSpinner.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.INVISIBLE);
    }

    private void requestUsers(UsersRequest ur, GetUsersTask.RequestType type) {
        CloudApi cloudApi = CloudApi.getCloudApi(null);
        cloudApi.getUsers(ur, type, this);
    }

}
