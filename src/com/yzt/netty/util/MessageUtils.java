package com.yzt.netty.util;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yzt.netty.client.WSClient;

/**
 * 
 * @Title:  MessageUtils     
 * @Description: 向外发送消息
 * @author: zc    
 * @date:   2018年1月16日 下午5:37:27   
 * @version V1.0 
 * @Copyright: 2018 云智通 All rights reserved.
 */
public class MessageUtils {

    public static String getHttpGetUri(String uri){
        int index = -1;
        if (uri != null && uri.length() > 0 && ((index = uri.indexOf("?")) > - 1)) {
            String requestUri = uri.substring(0 , index );
            return requestUri;
        }
        return uri;
    }

    public static Map<String , Object> getHttpGetParams(String uri){
        int index = -1;
        if (uri != null && uri.length() > 0 && ((index = uri.indexOf("?")) > - 1)) {
            String requestUri = uri.substring(index + 1);
            String[] reqs = requestUri.split("&");
            if (reqs != null && reqs.length > 0) {
                Map<String , Object> params = new HashMap<>();
                //name value 交替
                for (String req : reqs) {
                    String[] nameAndValue = req.split("=");
                    if (nameAndValue != null && nameAndValue.length == 2) {
                        String name = nameAndValue[0];
                        String value = nameAndValue[1];
                        params.put(name , value);
                    }
                }
                return params;
            }
        }
        return null;
    }


    /**
     * 判断是否含有特殊字符
     *
     * @param str
     * @return true为包含，false为不包含
     */
    public static boolean isSpecialChar(String str) {
        String regEx = "[ _`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]|\n|\r|\t";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        return m.find();
    }

    public static void sendMessage(Collection<WSClient> clients , String message){
        if (clients != null) {
            for (WSClient client : clients) {
                ChannelHandlerContext channelHandlerContext = client.getChannelHandlerContext();
                TextWebSocketFrame textFrame = new TextWebSocketFrame(message);
                channelHandlerContext.writeAndFlush(textFrame);
            }
        }
    }


    public static void sendMessage(WSClient client , String message){
    	if (client!=null) {
    		ChannelHandlerContext channelHandlerContext = client.getChannelHandlerContext();
            TextWebSocketFrame textFrame = new TextWebSocketFrame(message);
            channelHandlerContext.writeAndFlush(textFrame);
		}
    }


    public static void sendPingMessage(WSClient client){
        ChannelHandlerContext channelHandlerContext = client.getChannelHandlerContext();
        if (channelHandlerContext.channel().isWritable()) {
            PingWebSocketFrame ping = new PingWebSocketFrame();
            channelHandlerContext.writeAndFlush(ping);
        }
    }

}
