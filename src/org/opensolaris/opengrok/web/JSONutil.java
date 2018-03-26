/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.messages.Message;

/**
 * simple JSON utility methods
 * 
 * @author Krystof Tulinger
 * @author Vladimir Kotal
 */
public class JSONutil {
    /**
     * Converts an array into JSON array.
     *
     * @param array the input array
     * @return the output JSON array
     */
    @SuppressWarnings("unchecked")
    protected static JSONArray convertArrayToJSONArray(long[] array) {
        JSONArray ret = new JSONArray();
        for (long o : array) {
            ret.add(o);
        }
        return ret;
    }

    /**
     * Converts an JSON array into an array.
     *
     * @param dest the input JSON array
     * @param target the output array
     * @return target
     */
    protected static long[] convertJSONArrayToArray(JSONArray dest, long[] target) {
        for (int i = 0; i < target.length && i < dest.size(); i++) {
            target[i] = (long) dest.get(i);
        }
        return target;
    }

    
    /**
     * Print set of messages into json array
     *
     * @param set set of messages
     * @return json array containing the set of messages
     */
    @SuppressWarnings("unchecked")
    public static JSONArray messagesToJson(SortedSet<Message> set) {
        JSONArray array = new JSONArray();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        for (Message m : set) {
            JSONObject message = new JSONObject();
            message.put("class", Util.encode(m.getClassName()));
            message.put("expiration", Util.encode(df.format(m.getExpiration())));
            message.put("created", Util.encode(df.format(m.getCreated())));
            message.put("text", Util.encode(m.getText()));
            JSONArray tags = new JSONArray();
            for (String t : m.getTags()) {
                tags.add(Util.encode(t));
            }
            message.put("tags", tags);
            array.add(message);
        }
        return array;
    }

    /**
     * Print set of messages into json object for given tag.
     *
     * @param tag return messages in json format for the given tag
     * @return json object with 'tag' and 'messages' attribute or null
     */
    @SuppressWarnings("unchecked")
    public static JSONObject messagesToJsonObject(String tag) {
        SortedSet<Message> messages = RuntimeEnvironment.getInstance().getMessages(tag);
        if (messages.isEmpty()) {
            return null;
        }
        JSONObject toRet = new JSONObject();
        toRet.put("tag", tag);
        toRet.put("messages", messagesToJson(messages));
        return toRet;
    }

    /**
     * Print messages for given tags into json array
     *
     * @param array the array where the result should be stored
     * @param tags list of tags
     * @return json array of the messages (the same as the parameter)
     * @see #messagesToJsonObject(String)
     */
    @SuppressWarnings("unchecked")
    public static JSONArray messagesToJson(JSONArray array, String... tags) {
        array = array == null ? new JSONArray() : array;
        for (String tag : tags) {
            JSONObject messages = messagesToJsonObject(tag);
            if (messages == null || messages.isEmpty()) {
                continue;
            }
            array.add(messages);
        }
        return array;
    }

    /**
     * Print messages for given tags into json array
     *
     * @param tags list of tags
     * @return json array of the messages
     * @see #messagesToJson(JSONArray, String...)
     * @see #messagesToJsonObject(String)
     */
    public static JSONArray messagesToJson(String... tags) {
        return messagesToJson((JSONArray) null, tags);
    }

    /**
     * Print messages for given tags into json array
     *
     * @param tags list of tags
     * @return json array of the messages
     * @see #messagesToJson(String...)
     * @see #messagesToJsonObject(String)
     */
    public static JSONArray messagesToJson(List<String> tags) {
        String[] array = new String[tags.size()];
        return messagesToJson(tags.toArray(array));
    }

    /**
     * Print messages for given project into json array. These messages are
     * tagged by project description or tagged by any of the project's group
     * name.
     *
     * @param project the project
     * @param additionalTags additional list of tags
     * @return the json array
     * @see #messagesToJson(String...)
     */
    public static JSONArray messagesToJson(Project project, String... additionalTags) {
        if (project == null) {
            return new JSONArray();
        }
        List<String> tags = new ArrayList<>();
        tags.addAll(Arrays.asList(additionalTags));
        tags.add(project.getName());
        project.getGroups().stream().forEach((Group t) -> {
            tags.add(t.getName());
        });
        return messagesToJson(tags);
    }

    /**
     * Print messages for given project into json array. These messages are
     * tagged by project description or tagged by any of the project's group
     * name
     *
     * @param project the project
     * @return the json array
     * @see #messagesToJson(Project, String...)
     */
    public static JSONArray messagesToJson(Project project) {
        return messagesToJson(project, new String[0]);
    }

    /**
     * Print messages for given group into json array.
     *
     * @param group the group
     * @param additionalTags additional list of tags
     * @return the json array
     * @see #messagesToJson(java.util.List)
     */
    public static JSONArray messagesToJson(Group group, String... additionalTags) {
        List<String> tags = new ArrayList<>();
        tags.add(group.getName());
        tags.addAll(Arrays.asList(additionalTags));
        return messagesToJson(tags);
    }

    /**
     * Print messages for given group into json array.
     *
     * @param group the group
     * @return the json array
     * @see #messagesToJson(Group, String...)
     */
    public static JSONArray messagesToJson(Group group) {
        return messagesToJson(group, new String[0]);
    }

    /**
     * Convert statistics object into JSONObject.
     *
     * @param stats object containing statistics
     * @return the JSON object
     */
    public static JSONObject statisticToJson(Statistics stats) {
        return stats.toJson();
    }

    /**
     * Convert JSONObject object into statistics.
     *
     * @param input object containing statistics
     * @return the statistics object
     */
    public static Statistics jsonToStatistics(JSONObject input) {
        return Statistics.from(input);
    }

}
