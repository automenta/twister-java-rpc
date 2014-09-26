/*
 * Copyright (C) 2014 me
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package twister.rpc;

import java.io.IOException;
import java.util.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Extends Twister with methods for creating or editing content or profile state 
 */
public class TwisterEditing extends TwisterReading {

    public TwisterEditing(String userid, String serverURL, String serverUser, String serverPass) {
        super(userid, serverURL, serverUser, serverPass);
    }
    
    public void follow(String user) {
        (new TwisterReading.FollowTask()).executeOnExecutor(threads, user()
                .getId(), user);
    }

    public void sendNewTwist(String msg) {
        User walletUserProfile = getProfileOrCreateIfNotExist(user()
                .getId());
        (new TwisterReading.SendNewTwistTask()).executeOnExecutor(threads,
                user().getId(),
                "" + (walletUserProfile.getLatestPostIdOnServer() + 1), msg);
    }

    public void sendNewDirectMessage(String toUserId, String msg) {
        User walletUserProfile = getProfileOrCreateIfNotExist(user()
                .getId());

        (new TwisterReading.SendNewDirectMessageTask())
                .executeOnExecutor(threads,
                        user().getId(),
                        ""
                        + (walletUserProfile
                        .getLatestDirectMessageId(toUserId) + 1),
                        toUserId, msg);
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
            (new TwisterReading.GetLastHaveTask()).executeOnExecutor(threads,
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
            (new TwisterReading.GetLastHaveTask()).executeOnExecutor(threads,
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

    
    
}
