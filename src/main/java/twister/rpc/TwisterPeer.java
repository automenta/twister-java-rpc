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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Functions for accessing Twister peer services that does not involve a particular user/wallet
 */
public class TwisterPeer {
    public final String url;

    private TreeMap<String, WalletUser> walletUsersList = new TreeMap<String, WalletUser>();
    
    protected ExecutorService threads = Executors.newFixedThreadPool(THREADS);
    
    private static final int THREADS = 10;
    private static final int MAX_HTTP_CONNECTION = 20;
    static final int DEFAULT_GET_POSTS = 50;
    
    protected ScheduledExecutorService pollingThread = Executors.newScheduledThreadPool(1);
           
    protected PoolingHttpClientConnectionManager conman = new PoolingHttpClientConnectionManager();
    private final String auth;

    public TwisterPeer(String serverURL, String serverUser, String serverPass) {
        this.url = serverURL;
       
        conman.setMaxTotal(MAX_HTTP_CONNECTION);
                
        this.auth = Base64.getEncoder().encodeToString((serverUser + ':' + serverPass).getBytes());
        
         
    }

    

    
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
    

    public TreeMap<String, WalletUser> getWalletUsersList() {
        return walletUsersList;
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
 
    
    
}
