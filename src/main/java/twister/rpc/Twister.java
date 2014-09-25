package twister.rpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class Twister {

    public final String url;
    private WalletUser wallet;
    private TreeMap<Post, Post> spamPostsList = new TreeMap<Post, Post>();
    private TreeMap<String, WalletUser> walletUsersList = new TreeMap<String, WalletUser>();

    private TreeMap<String, User> followingUsers = new TreeMap<String, User>();
    private TreeMap<Post, Post> followingPosts = new TreeMap<Post, Post>();

    private TreeMap<Post, Post> posts = new TreeMap<Post, Post>();

    private TreeMap<DirectMessage, DirectMessage> directs = new TreeMap<DirectMessage, DirectMessage>();

    private Map<String, User> profiles = new HashMap<String, User>();

    private List<FollowingUsersListListener> followingUsersListListeners = new ArrayList<FollowingUsersListListener>();
    private List<PostsListListener> postsListListeners = new ArrayList<PostsListListener>();
    private List<DirectMessagesListListener> directMessagesListListeners = new ArrayList<DirectMessagesListListener>();

    private ExecutorService threads = Executors.newFixedThreadPool(THREADS);
    
    private static final int THREADS = 10;
    private static final int MAX_HTTP_CONNECTION = 20;
    private static final int DEFAULT_GET_POSTS = 50;
    
    private ScheduledExecutorService pollingThread = Executors.newScheduledThreadPool(1);
           
    PoolingHttpClientConnectionManager conman = new PoolingHttpClientConnectionManager();
    private final String auth;

    public CloseableHttpClient newClient() {
        //return HttpClients.custom().setConnectionManager(conman).build();
        return HttpClients.createDefault();
    }
 
   
    public HttpPost newPost() {
        HttpPost p = new HttpPost(url);
        p.setHeader("Authorization", "Basic " + auth);
        return p;
    }
    
    public HttpPost newPost(JSONObject entity) {
        HttpPost p = newPost();
        p.setEntity(new StringEntity(entity.toString(), "utf-8"));
        return p;
    }
    public HttpPost newPost(String method, int id, Object params) {
        HttpPost p = newPost();
        JSONObject x = new JSONObject()
                .put("method", method)
                .put("id", id)                
                .put("jsonrpc", "2.0");
        if (params!=null)
            x.put("params", params);
        p.setEntity(new StringEntity(x.toString(), "utf-8"));
        return p;
    }

    
    
    public Twister(String userid, String serverURL, String serverUser, String serverPass) {
        conman.setMaxTotal(MAX_HTTP_CONNECTION);
        
        
        this.auth = Base64.getEncoder().encodeToString((serverUser + ':' + serverPass).getBytes());
        
                
        wallet = new WalletUser(userid);
        this.url = serverURL;

        pollingThread.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                threads.execute(new GetWalletUsersTask() {
                    @Override public void result(List<String> users) {
                        System.out.println("Wallet users: " + users);
                    }                    
                });
            }
        }, 0, 30, TimeUnit.SECONDS);
        

        pollingThread.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                threads.execute(new GetLastHaveTask(user()));
            }
        }, 0, 30, TimeUnit.SECONDS);

        pollingThread.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                threads.execute(new GetSpamPostsTask());
            }
        }, 0, 10, TimeUnit.MINUTES);
    }


    public Map<String, User> getProfiles() {
        return profiles;
    }

    public WalletUser user() {
        return wallet;
    }

//    private void setCurrentWalletUser(WalletUser currentWalletUser) {
//        /*Log.i(TAG,
//                "setting current wallet user to " + currentWalletUser.getId()
//                + " from " + this.currentWalletUser.getId());*/
//        
//        this.wallet = currentWalletUser;
//
//        getCurrentWalletUserFollowingUsersList().clear();
//        getCurrentWalletUserFollowingPostsList().clear();
//        getCurrentWalletUserPostsList().clear();
//        getCurrentWalletUserDirectMessagesList().clear();
//
//        getCurrentWalletUserPostsList().putAll(getSpamPostsList());
//        notifyFollowingUsersListListener();
//        notifyPostsListListener();
//
//        threads.execute(new GetLastHaveTask(getCurrentWalletUser()));
//
//    }

//    public void setCurrentWalletUser(String walletUser) {
//        setCurrentWalletUser(getWalletUsersList().get(walletUser));
//    }

    public TreeMap<String, WalletUser> getWalletUsersList() {
        return walletUsersList;
    }

    public TreeMap<String, User> getCurrentWalletUserFollowingUsersList() {
        return followingUsers;
    }

    public TreeMap<Post, Post> getCurrentWalletUserPostsList() {
        return posts;
    }

    public TreeMap<DirectMessage, DirectMessage> getCurrentWalletUserDirectMessagesList() {
        return directs;
    }

    public TreeMap<Post, Post> getSpamPostsList() {
        return spamPostsList;
    }

    public TreeMap<Post, Post> getCurrentWalletUserFollowingPostsList() {
        return followingPosts;
    }

    public void follow(String user) {
        (new FollowTask()).executeOnExecutor(threads, user()
                .getId(), user);
    }

    public void sendNewTwist(String msg) {
        User walletUserProfile = getProfileOrCreateIfNotExist(user()
                .getId());
        (new SendNewTwistTask()).executeOnExecutor(threads,
                user().getId(),
                "" + (walletUserProfile.getLatestPostIdOnServer() + 1), msg);
    }

    public void sendNewDirectMessage(String toUserId, String msg) {
        User walletUserProfile = getProfileOrCreateIfNotExist(user()
                .getId());

        (new SendNewDirectMessageTask())
                .executeOnExecutor(threads,
                        user().getId(),
                        ""
                        + (walletUserProfile
                        .getLatestDirectMessageId(toUserId) + 1),
                        toUserId, msg);
    }

    public void addFollowingUsersListListener(FollowingUsersListListener f) {
        followingUsersListListeners.add(f);
    }

    public void notifyFollowingUsersListListener() {
        for (FollowingUsersListListener f : followingUsersListListeners) {
            f.onFollowingUsersListChanged();
        }
    }

    public interface FollowingUsersListListener {

        public void onFollowingUsersListChanged();
    }

    public void addPostsListListener(PostsListListener f) {
        postsListListeners.add(f);
    }

    public void notifyPostsListListener() {
        for (PostsListListener f : postsListListeners) {
            f.onPostsListChanged();
        }
    }

    public interface PostsListListener {

        public void onPostsListChanged();
    }

    public void addDirectMessagesListListener(DirectMessagesListListener f) {
        directMessagesListListeners.add(f);
    }

    public void notifyDirectMessagesListListener() {
        for (DirectMessagesListListener f : directMessagesListListeners) {
            f.onDirectMessagesListChanged();
        }
    }

    public interface DirectMessagesListListener {

        public void onDirectMessagesListChanged();
    }

    private User getProfileOrCreateIfNotExist(final String userId) {
        User user = profiles.get(userId);
        if (user == null) {
            user = new User(userId);
            profiles.put(userId, user);

            pollingThread.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    threads.execute(new GetProfileTask(userId));
                }
            }, 0, 2, TimeUnit.HOURS);
        }
        return user;
    }

    private class GetProfileTask implements Runnable {
        private final String userId;

        public GetProfileTask(String userId) {
            super();
            this.userId = userId;
        }

        @Override
        public void run() {
            try {
                CloseableHttpClient mClient = newClient();
                
                HttpPost request = newPost("dhtget", 1, 
                        new JSONArray().put(userId).put("profile").put("s") );

                
                GetProfileHandler responseHandler = new GetProfileHandler();

                Map<String, String> result = mClient.execute(request,responseHandler);
                
                mClient.close();
                
                if (result.get("id") != null) {
                    User user = profiles.get(result.get("id"));

                    // TODO check version/seq pls
                    if (user != null) {
                        String id = result.get("id");
                        user.setName(result.get("name").length() > 0 ? result
                                .get("name") : "@" + id);
                        user.setBio(result.get("bio"));
                        user.setLocation(result.get("location"));
                        user.setUrl(result.get("url"));

                        threads.execute(new GetAvatarTask(id));

                        Twister.this.notifyFollowingUsersListListener();
                        Twister.this.notifyPostsListListener();
                    }
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private class GetProfileHandler implements
            ResponseHandler<Map<String, String>> {

        private static final String TAG = "GetProfileHandler";

        @Override
        public Map<String, String> handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            Map<String, String> result = new HashMap<String, String>();

			// String JSONResponse = new
            // BasicResponseHandler().handleResponse(response);
            String JSONResponse = EntityUtils.toString(response.getEntity(),
                    "utf-8");
            // Log.i(TAG, "response: " + JSONResponse);

            try {
                JSONObject responseObject = (JSONObject) new JSONTokener(
                        JSONResponse).nextValue();

                JSONObject profile = responseObject.getJSONArray("result")
                        .getJSONObject(0).getJSONObject("p");

                result.put("id", profile.getJSONObject("target").getString("n"));
                JSONObject v = profile.optJSONObject("v");

                if (v != null) {
					// result.put("name",
                    // new String(v.optString("fullname",
                    // result.get("id")).getBytes(), "utf-8"));
                    // result.put("bio", new String(v.optString("bio",
                    // "").getBytes(), "utf-8"));
                    result.put("name", v.optString("fullname", ""));
                    result.put("bio", v.optString("bio", ""));
                    result.put("location", v.optString("location", ""));
                    result.put("url", v.optString("url", ""));
                }

            } catch (JSONException e) {
                // e.printStackTrace();
            }

            return result;
        }
    }

    private class GetLastHaveTask implements Runnable {
        private final WalletUser u;

        public GetLastHaveTask(WalletUser requestForWalletUser) {
            super();
            this.u = requestForWalletUser;
        }

        @Override
        public void run() {
            try {
                CloseableHttpClient mClient = newClient();
                HttpPost request = newPost("getlasthave", 1, new JSONArray().put(u.getId()));
                GetLastHaveHandler responseHandler = new GetLastHaveHandler();

                Map<String, Integer> result = mClient.execute(request, responseHandler);
                mClient.close();
                
                if (result != null && u.equals(user())) {
                    for (final Entry<String, Integer> lastHave : result.entrySet()) {

                        User user = getProfileOrCreateIfNotExist(lastHave.getKey());
                        if (!followingUsers.containsKey(lastHave.getKey())) {
                            followingUsers.put(lastHave.getKey(), user);
                            followingPosts.putAll(user.getPosts());
                            posts.putAll(user.getPosts());
                            directs.putAll(u.getDirectMessages(user.getId()));

                            Twister.this.notifyPostsListListener();
                            Twister.this.notifyFollowingUsersListListener();
                        }

                        // fetch posts
                        if (lastHave.getValue() > user.getLatestPostIdOnServer()) {
                            user.setLatestPostIdOnServer(lastHave.getValue());

                            threads.execute(new GetPostsTask(DEFAULT_GET_POSTS, user));
                        }

                        // fetch direct messages
                        threads.execute(new GetDirectMessagesTask(
                                getProfileOrCreateIfNotExist(u.getId()), user));
                    }
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private class GetLastHaveHandler implements
            ResponseHandler<Map<String, Integer>> {

        @Override
        public Map<String, Integer> handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            Map<String, Integer> result = new HashMap<String, Integer>();
            String JSONResponse = new BasicResponseHandler()
                    .handleResponse(response);
            // Log.i(TAG, "response: " + JSONResponse);

            try {
                JSONObject responseObject = (JSONObject) new JSONTokener(
                        JSONResponse).nextValue();

                JSONObject lastHaveList = responseObject
                        .getJSONObject("result");

                for (Iterator i = lastHaveList.keys(); i.hasNext();) {
                    String id = (String) i.next();
                    result.put(id, lastHaveList.optInt(id, -1));
                }

            } catch (JSONException e) {
                // e.printStackTrace();
            }

            return result;
        }
    }

    abstract class GetWalletUsersTask implements Runnable {
 
        public GetWalletUsersTask() {
            super();
        }

        public void run() {
            try {
                CloseableHttpClient http = newClient();
                HttpPost request = newPost("listwalletusers",1,null);

                GetWalletUsersHandler responseHandler = new GetWalletUsersHandler();

                
                List<String> result = http.execute(request, responseHandler);
                if (result != null) {
                    for (String id : result) {
                        if (walletUsersList.get(id) == null) {
                            walletUsersList.put(id, new WalletUser(id));
                        }
                    }
                }
                http.close();
                
                result(result);

            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        
        abstract public void result(List<String> users);

    }

    private class GetWalletUsersHandler implements
            ResponseHandler<List<String>> {

        private static final String TAG = "GetWalletUsersHandler";

        @Override
        public List<String> handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            List<String> result = new ArrayList<String>();
            String JSONResponse = new BasicResponseHandler()
                    .handleResponse(response);
            // Log.i(TAG, "response: " + JSONResponse);

            try {
                JSONObject responseObject = (JSONObject) new JSONTokener(
                        JSONResponse).nextValue();

                JSONArray walletUsersList = responseObject
                        .getJSONArray("result");

                for (int i = 0; i < walletUsersList.length(); i++) {
                    result.add(walletUsersList.getString(i));
                }

            } catch (JSONException e) {
                // e.printStackTrace();
            }
            

            return result;
        }
    }
 
    
    private class GetPostsTask implements Runnable {

        private User user;
        private int count;

        public GetPostsTask(int count, User... params) {
            super();
            this.count = count;
            this.user = params[0];
        }

        @Override
        public void run() {
            
            try {
                CloseableHttpClient client = newClient();
                
                int max_id = user.getLatestPostIdOnServer();
                
                if (user.getLatestPostId() > -1) {
                    count = user.getLatestPostIdOnServer() - user.getLatestPostId() + 1;
                }

                HttpPost request = newPost("getposts", 1,                        
                    new JSONArray().
                            put(count).
                            put(new JSONArray().
                                    put(new JSONObject().
                                            put("username", user.getId()).
                                            put("max_id", max_id)))
                );

                
                GetPostsHandler responseHandler = new GetPostsHandler();

                TreeMap<Post, Post> result = client.execute(request, responseHandler);
                client.close();
                
                user.getPosts().putAll(result);
                if (followingUsers.containsKey(user.getId())) {
                    followingPosts.putAll(result);
                    posts.putAll(result);
                    
                    System.out.println("posts: "+ posts);
                    Twister.this.notifyPostsListListener();
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private class GetPostsHandler implements
            ResponseHandler<TreeMap<Post, Post>> {

        private static final String TAG = "GetPostsHandler";

        @Override
        public TreeMap<Post, Post> handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            TreeMap<Post, Post> result = new TreeMap<Post, Post>();
			// String JSONResponse = new
            // BasicResponseHandler().handleResponse(response);
            String JSONResponse = EntityUtils.toString(response.getEntity(),
                    "utf-8");
            // Log.i(TAG, "response: " + JSONResponse);

            try {
                JSONObject responseObject = (JSONObject) new JSONTokener(
                        JSONResponse).nextValue();

                JSONArray posts = responseObject.getJSONArray("result");

                for (int i = 0; i < posts.length(); i++) {
                    JSONObject post = posts.getJSONObject(i).optJSONObject(
                            "userpost");
                    if (post != null) {
                        Post userPost = new Post();

                        userPost.setHeight(post.optInt("height", -1));
                        userPost.setId(post.optInt("k", -1));
                        userPost.setPrevId(post.optInt("lastk", -1));
                        userPost.setTime(post.optLong("time", -1));
                        userPost.setUserId(post.optString("n", ""));
                        userPost.setMsg(post.optString("msg"));
                        JSONObject reply = post.optJSONObject("reply");
                        if (reply != null) {
                            userPost.setReplyUserId(reply.optString("n"));
                            userPost.setReplyUserPostId(reply.optInt("k"));
                        }
                        JSONObject rt = post.optJSONObject("rt");
                        if (rt != null) {
                            Post rtPost = new Post();

                            String userid = rt.optString("n", "");
                            if (userid.length() > 0) {
                                getProfileOrCreateIfNotExist(userid);
                            }
                            rtPost.setHeight(rt.optInt("height", -1));
                            rtPost.setId(rt.optInt("k", -1));
                            rtPost.setPrevId(rt.optInt("lastk", -1));
                            rtPost.setTime(rt.optLong("time", -1));
                            rtPost.setUserId(userid);
                            rtPost.setMsg(rt.optString("msg"));

                            userPost.setReTwistPost(rtPost);
                        }
                        result.put(userPost, userPost);
                    }

                }

            } catch (JSONException e) {
                // e.printStackTrace();
            }

            return result;
        }
    }

    private class GetDirectMessagesTask implements Runnable {


        private User fromUser;
        private User toUser;

        public GetDirectMessagesTask(User fromUser, User toUser) {
            super();
            this.fromUser = fromUser;
            this.toUser = toUser;
        }

        @Override
        public void run() {
            try {
                int since_id = fromUser
                        .getLatestDirectMessageId(toUser.getId());
                int count = 100;

                JSONArray b = new JSONArray();
                b.put(fromUser.getId());
                b.put(count);

                JSONArray c = new JSONArray();
                JSONObject d = new JSONObject();
                d.put("username", toUser.getId());
                d.put("since_id", since_id);
                c.put(d);
                b.put(c);

                CloseableHttpClient mClient = newClient();
                HttpPost request = newPost("getdirectmsgs", 1, b);

                GetDirectMessagesHandler responseHandler = new GetDirectMessagesHandler();

                TreeMap<DirectMessage, DirectMessage> result = mClient.execute(request, responseHandler);
                mClient.close();
                
                fromUser.getDirectMessages(toUser.getId()).putAll(result);
                if (wallet.getId().equals(fromUser.getId())) {
                    directs.putAll(result);
                    Twister.this.notifyDirectMessagesListListener();
                }
                
                
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private class GetDirectMessagesHandler implements
            ResponseHandler<TreeMap<DirectMessage, DirectMessage>> {

        private static final String TAG = "GetDirectMessagesHandler";

        @Override
        public TreeMap<DirectMessage, DirectMessage> handleResponse(
                HttpResponse response) throws ClientProtocolException,
                IOException {
            TreeMap<DirectMessage, DirectMessage> result = new TreeMap<DirectMessage, DirectMessage>();
			// String JSONResponse = new
            // BasicResponseHandler().handleResponse(response);
            String JSONResponse = EntityUtils.toString(response.getEntity(),
                    "utf-8");
            // Log.i(TAG, "response: " + JSONResponse);

            try {
                JSONObject responseObject = (JSONObject) new JSONTokener(
                        JSONResponse).nextValue();

                JSONObject r = responseObject.getJSONObject("result");

                Iterator key = r.keys();
                if (key.hasNext()) {
                    String userid = (String) key.next();
                    JSONArray directMessages = r.getJSONArray(userid);

                    for (int i = 0; i < directMessages.length(); i++) {
                        JSONObject directMessage = directMessages
                                .getJSONObject(i);
                        if (directMessage != null) {
                            DirectMessage userDirectMessage = new DirectMessage();

                            userDirectMessage.setId(directMessage.optInt("id",
                                    -1));
                            userDirectMessage.setTime(directMessage.optLong(
                                    "time", -1));
                            userDirectMessage.setUserId(userid);
                            userDirectMessage.setMsg(directMessage
                                    .optString("text"));
                            userDirectMessage.setFromMe(directMessage
                                    .optBoolean("fromMe", true));

                            result.put(userDirectMessage, userDirectMessage);
                        }

                    }
                }

            } catch (JSONException e) {
                // e.printStackTrace();
            }

            return result;
        }
    }

    private class GetSpamPostsTask implements Runnable {


        @Override
        public void run() {
            try {
                CloseableHttpClient mClient = newClient();
                
                int NUM_SPAM_POSTS = 10;
                HttpPost request = newPost("getspamposts", 1, new JSONArray().put(NUM_SPAM_POSTS));

                GetSpamPostsHandler responseHandler = new GetSpamPostsHandler();

                TreeMap<Post, Post> result = mClient.execute(request, responseHandler);
                mClient.close();
                
                if (result != null) {
                    // fetch profile, if needed
                    for (final Entry<Post, Post> p : result.entrySet()) {
                        getProfileOrCreateIfNotExist(p.getKey().getUserId());
                    }

                    spamPostsList.putAll(result);
                    posts.putAll(result);
                    Twister.this.notifyPostsListListener();
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private class GetSpamPostsHandler implements
            ResponseHandler<TreeMap<Post, Post>> {

        private static final String TAG = "GetSpamPostsHandler";

        @Override
        public TreeMap<Post, Post> handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            TreeMap<Post, Post> result = new TreeMap<Post, Post>();
			// String JSONResponse = new
            // BasicResponseHandler().handleResponse(response);
            String JSONResponse = EntityUtils.toString(response.getEntity(),
                    "utf-8");
            // Log.i(TAG, "response: " + JSONResponse);

            try {
                JSONObject responseObject = (JSONObject) new JSONTokener(
                        JSONResponse).nextValue();

                JSONArray posts = responseObject.getJSONArray("result");

                for (int i = 0; i < posts.length(); i++) {
                    JSONObject post = posts.getJSONObject(i).optJSONObject(
                            "userpost");
                    if (post != null) {
                        Post spamPost = new Post();

                        spamPost.setHeight(post.optInt("height", -1));
                        spamPost.setId(post.optInt("k", -1));
                        spamPost.setTime(post.optLong("time", -1));
                        spamPost.setUserId(post.optString("n", ""));
						// spamPost.setMsg(new String(post.optString("msg",
                        // "").getBytes(), "utf-8"));
                        spamPost.setMsg(post.optString("msg", ""));

                        result.put(spamPost, spamPost);
                    }

                }

            } catch (JSONException e) {
                // e.printStackTrace();
            }

            return result;
        }
    }

    private class GetAvatarTask implements Runnable {
        
        private String id;

        private GetAvatarTask(String id) {
            this.id = id;
        }

        @Override
        public void run() {
            try {
                
                CloseableHttpClient mClient = newClient();
                HttpPost request = newPost("dhtget", 1, new JSONArray().put(id).put("avatar").put("s"));
                
                GetAvatarHandler responseHandler = new GetAvatarHandler();

                String result = mClient.execute(request, responseHandler);
                mClient.close();

                if (result != null && result.length() > 0) {
                    User user = profiles.get(id);
                    if (user != null) {
                        if (result.startsWith("data:image/jpeg;base64,")) {
                            int i = result.indexOf(',');
//                            byte[] bMapArray = Base64.decode(
//                                    result.substring(result.indexOf(',') + 1),
//                                    Base64.DEFAULT);
//                            
//                            Bitmap avatar = BitmapFactory.decodeByteArray(
//                                    bMapArray, 0, bMapArray.length);
//                            user.setAvatar(avatar);
//
//                            Twister.this.notifyFollowingUsersListListener();
//                            Twister.this.notifyPostsListListener();
                        }
                    }
                }
                
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private class GetAvatarHandler implements ResponseHandler<String> {

        @Override
        public String handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            String result = "";
            String JSONResponse = new BasicResponseHandler()
                    .handleResponse(response);
            // Log.i(TAG, "response: " + JSONResponse);

            try {
                JSONObject responseObject = (JSONObject) new JSONTokener(
                        JSONResponse).nextValue();

                JSONObject profile = responseObject.getJSONArray("result")
                        .getJSONObject(0).getJSONObject("p");

				// result.put("id",
                // profile.getJSONObject("target").getString("n"));
                result = profile.optString("v", "");

            } catch (JSONException e) {
                // e.printStackTrace();
            }

            return result;
        }
    }

    private class FollowTask extends AsyncTask<String, Void, Void> {

        private static final String TAG = "FollowTask";

        private AndroidHttpClient mClient = AndroidHttpClient.newInstance("");

        public FollowTask() {
            super();
        }

        @Override
        protected Void doInBackground(String... params) {
            try {
                HttpPost request = new HttpPost(url);
                JSONArray c = new JSONArray();
                c.put(params[1]);

                JSONArray b = new JSONArray();
                b.put(params[0]);
                b.put(c);

                JSONObject a = new JSONObject();
                a.put("method", "follow");
                a.put("id", 1);
                a.put("params", b);

                request.setEntity(new StringEntity(a.toString(), "utf-8"));

                request.setHeader(
                        "Authorization",
                        "Basic "
                        + Base64.encodeToString("user:pwd".getBytes(),
                                Base64.NO_WRAP));
                FollowTaskHandler responseHandler = new FollowTaskHandler();

                mClient.execute(request, responseHandler);
                mClient.close();
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
        }
    }

    private class FollowTaskHandler implements ResponseHandler<Void> {

        private static final String TAG = "FollowTaskHandler";

        @Override
        public Void handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            return null;
        }
    }

    private class SendNewTwistTask extends AsyncTask<String, Void, Void> {

        private static final String TAG = "SendNewTwistTask";

        private AndroidHttpClient mClient = AndroidHttpClient.newInstance("");

        public SendNewTwistTask() {
            super();
        }

        @Override
        protected Void doInBackground(String... params) {
            try {
                HttpPost request = new HttpPost(url);

                JSONArray b = new JSONArray();
                b.put(params[0]);
                b.put(Integer.valueOf(params[1]));
                b.put(params[2]);

                JSONObject a = new JSONObject();
                a.put("method", "newpostmsg");
                a.put("id", 1);
                a.put("params", b);

                request.setEntity(new StringEntity(a.toString(), "utf-8"));

                request.setHeader(
                        "Authorization",
                        "Basic "
                        + Base64.encodeToString("user:pwd".getBytes(),
                                Base64.NO_WRAP));
                SendNewTwistTaskHandler responseHandler = new SendNewTwistTaskHandler();

                mClient.execute(request, responseHandler);
                mClient.close();
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            (new GetLastHaveTask()).executeOnExecutor(threads,
                    user());
        }
    }

    private class SendNewTwistTaskHandler implements ResponseHandler<Void> {

        private static final String TAG = "SendNewTwistTaskHandler";

        @Override
        public Void handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            return null;
        }
    }

    private class SendNewDirectMessageTask extends
            AsyncTask<String, Void, Void> {

        private static final String TAG = "SendNewDirectMessageTask";

        private AndroidHttpClient mClient = AndroidHttpClient.newInstance("");

        public SendNewDirectMessageTask() {
            super();
        }

        @Override
        protected Void doInBackground(String... params) {
            try {
                HttpPost request = new HttpPost(url);

                JSONArray b = new JSONArray();
                b.put(params[0]);
                b.put(Integer.valueOf(params[1]));
                b.put(params[2]);
                b.put(params[3]);

                JSONObject a = new JSONObject();
                a.put("method", "newdirectmsg");
                a.put("id", 1);
                a.put("params", b);

                request.setEntity(new StringEntity(a.toString(), "utf-8"));

                request.setHeader(
                        "Authorization",
                        "Basic "
                        + Base64.encodeToString("user:pwd".getBytes(),
                                Base64.NO_WRAP));
                SendNewDirectMessageTaskHandler responseHandler = new SendNewDirectMessageTaskHandler();

                mClient.execute(request, responseHandler);
                mClient.close();
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            (new GetLastHaveTask()).executeOnExecutor(threads,
                    user());
        }
    }

    private class SendNewDirectMessageTaskHandler implements
            ResponseHandler<Void> {

        private static final String TAG = "SendNewDirectMessageTaskHandler";

        @Override
        public Void handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            return null;
        }
    }

    public List<Map<String, String>> autocomplete(String input) {

        List<Map<String, String>> result = new ArrayList<Map<String, String>>();

        try {
            HttpPost request = new HttpPost(url);
            JSONArray b = new JSONArray();
            b.put(input);
            b.put(5);

            JSONObject a = new JSONObject();
            a.put("method", "listusernamespartial");
            a.put("id", 1);
            a.put("params", b);

            request.setEntity(new StringEntity(a.toString(), "utf-8"));

            request.setHeader(
                    "Authorization",
                    "Basic "
                    + Base64.encodeToString("user:pwd".getBytes(),
                            Base64.NO_WRAP));

            List<String> userids = mClient.execute(request,
                    new ResponseHandler<List<String>>() {
                        @Override
                        public List<String> handleResponse(HttpResponse response)
                        throws ClientProtocolException, IOException {
                            List<String> result = new ArrayList<String>();
                            String JSONResponse = new BasicResponseHandler()
                            .handleResponse(response);
                            // Log.i(TAG, "response: " + JSONResponse);

                            try {
                                JSONObject responseObject = (JSONObject) new JSONTokener(
                                        JSONResponse).nextValue();

                                JSONArray usersList = responseObject
                                .getJSONArray("result");

                                for (int i = 0; i < usersList.length(); i++) {
                                    result.add(usersList.getString(i));
                                }

                            } catch (JSONException e) {
                            }
                            return result;
                        }
                    });

            for (String userid : userids) {
                Map<String, String> userinfo = new HashMap<String, String>();
                userinfo.put("id", userid);
                if (profiles.get(userid) != null) {
                    userinfo.put("name", profiles.get(userid).getName());
                } else {
                    userinfo.put("name", userid);
                }
                result.add(userinfo);
            }

        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }
}
