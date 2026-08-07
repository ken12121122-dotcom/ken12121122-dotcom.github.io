package com.amin.pocketgba;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Central runtime registry for graph capabilities. Manifest metadata remains the authority. */
final class NodeRegistry {
    static final String META_VISIBLE="amin.graph.visible", META_ID="amin.graph.id", META_CAPABILITY="amin.graph.capability_id", META_TITLE="amin.graph.title", META_DESCRIPTION="amin.graph.description", META_PARENT="amin.graph.parent", META_ROUTE="amin.graph.route", META_TYPE="amin.graph.node_type", META_ACTIONS="amin.graph.actions", META_INPUT="amin.graph.input_contract", META_OUTPUT="amin.graph.output_contract", META_VOICE_ENABLED="amin.graph.voice_enabled", META_VOICE_ALIASES="amin.graph.voice_aliases", META_STATUS="amin.graph.status", META_VERSION="amin.graph.version", META_STORAGE_ADAPTER="amin.graph.storage_adapter", META_STORAGE_SOURCE="amin.graph.storage_source_id", META_STORAGE_TABLE="amin.graph.storage_table", META_FILTER_JSON="amin.graph.filter_json", META_INPUT_CONTEXT_JSON="amin.graph.input_context_json";

    static final class Match { final JSONObject node; final String alias; Match(JSONObject node,String alias){this.node=node;this.alias=alias;} }
    private NodeRegistry(){}

    static String navigationJson(Context context){JSONArray pages=new JSONArray();try{PackageManager pm=context.getPackageManager();ActivityInfo[] infos=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_ACTIVITIES|PackageManager.GET_META_DATA).activities;if(infos!=null)for(ActivityInfo info:infos){Bundle m=info.metaData;if(m==null||!m.getBoolean(META_VISIBLE,false))continue;pages.put(pageFromMetadata(info,m));}}catch(Exception ignored){}
        try{return new JSONObject().put("format","amin-app-navigation").put("version",3).put("rootId","app-core").put("rootCapabilityId","app:app-core").put("rootTitle","Amin Pocket").put("rootDescription","Amin Pocket capability root").put("rootRoute","amin-home://open").put("pages",pages).toString();}catch(Exception e){return"{\"pages\":[]}";}}
    static String registryJson(Context c,NodeMetadataStore o){String b=GraphContract.nodeRegistryJson(navigationJson(c));return o==null?b:o.applyToRegistry(b);}
    static String typedEdgesJson(Context c,NodeMetadataStore o){String b=GraphContract.typedEdgesJson(navigationJson(c));return o==null?b:o.mergeEdges(b);}

    static JSONObject findNode(Context c,NodeMetadataStore o,String id){String n=clean(id);if(n.isEmpty())return null;try{JSONArray a=new JSONObject(registryJson(c,o)).optJSONArray("nodes");if(a==null)return null;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&(n.equals(x.optString("node_id"))||n.equals(x.optString("nodeId"))||n.equals(x.optString("capability_id"))||n.equals(x.optString("capabilityId"))))return x;}}catch(Exception ignored){}return null;}
    static JSONObject findByRoute(Context c,NodeMetadataStore o,String route){String n=clean(route);if(n.isEmpty())return null;try{JSONArray a=new JSONObject(registryJson(c,o)).optJSONArray("nodes");if(a==null)return null;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&n.equals(x.optString("route")))return x;}}catch(Exception ignored){}return null;}

    static Match matchVoice(Context c,NodeMetadataStore o,String transcript){String normalized=VoiceCommandParser.normalize(transcript);if(normalized.isEmpty())return null;Match candidate=null;int best=-1;try{JSONArray nodes=new JSONObject(registryJson(c,o)).optJSONArray("nodes");if(nodes==null)return null;for(int i=0;i<nodes.length();i++){JSONObject n=nodes.optJSONObject(i);if(n==null)continue;JSONObject voice=n.optJSONObject("voice");if(voice==null||!voice.optBoolean("enabled",false))continue;JSONArray aliases=voice.optJSONArray("aliases");if(aliases==null)continue;for(int j=0;j<aliases.length();j++){String alias=aliases.optString(j,"");String a=VoiceCommandParser.normalize(alias);if(a.isEmpty())continue;boolean exact=normalized.equals(a),contains=normalized.contains(a)||a.contains(normalized);if(!exact&&!contains)continue;int score=exact?10000+a.length():a.length();if(score>best){best=score;candidate=new Match(n,alias);}}}}catch(Exception ignored){}return candidate;}

    private static JSONObject pageFromMetadata(ActivityInfo info,Bundle m)throws Exception{JSONObject p=new JSONObject();String graphId=m.getString(META_ID,info.name);String title=m.getString(META_TITLE,info.name.substring(info.name.lastIndexOf('.')+1));p.put("id",graphId);p.put("capabilityId",GraphContract.capabilityId(GraphContract.APP_ORIGIN,graphId,m.getString(META_CAPABILITY,"")));p.put("title",title);p.put("description",m.getString(META_DESCRIPTION,""));p.put("parent",m.getString(META_PARENT,"app-core"));p.put("route",m.getString(META_ROUTE,""));p.put("direction",m.getString("amin.graph.direction","right"));p.put("slot",m.getInt("amin.graph.slot",0));p.put("activity",info.name);p.put("origin","app");p.put("locked",true);p.put("nodeType",m.getString(META_TYPE,"capability"));p.put("status",m.getString(META_STATUS,"active"));p.put("nodeVersion",m.getString(META_VERSION,"1"));p.put("actions",csv(m.getString(META_ACTIONS,"open")));p.put("inputContract",m.getString(META_INPUT,""));p.put("outputContract",m.getString(META_OUTPUT,""));
        JSONObject voice=new JSONObject();boolean ve=m.containsKey(META_VOICE_ENABLED)?m.getBoolean(META_VOICE_ENABLED,false):!m.getString(META_ROUTE,"").isEmpty();voice.put("enabled",ve);JSONArray aliases=csv(m.getString(META_VOICE_ALIASES,""));if(aliases.length()==0&&ve)aliases.put(title);voice.put("aliases",aliases);p.put("voice",voice);
        p.put("storage",new JSONObject().put("adapter",m.getString(META_STORAGE_ADAPTER,"")).put("source_id",m.getString(META_STORAGE_SOURCE,"")).put("table",m.getString(META_STORAGE_TABLE,"")));
        p.put("filter",jsonObject(m.getString(META_FILTER_JSON,"")));p.put("inputContext",jsonObject(m.getString(META_INPUT_CONTEXT_JSON,"")));return p;}
    private static JSONObject jsonObject(String s){try{return clean(s).isEmpty()?new JSONObject():new JSONObject(s);}catch(Exception e){return new JSONObject();}}
    private static JSONArray csv(String value){JSONArray out=new JSONArray();for(String item:split(value))out.put(item);return out;}
    private static List<String> split(String value){List<String> out=new ArrayList<>();if(value==null)return out;for(String item:value.split("[,，|]")){String c=clean(item);if(!c.isEmpty())out.add(c);}return out;}
    private static String clean(String value){return value==null?"":value.trim();}
}
