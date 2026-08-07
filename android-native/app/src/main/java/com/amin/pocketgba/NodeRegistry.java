package com.amin.pocketgba;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Central runtime registry for graph capabilities. Manifest metadata remains the authority for Activities; virtual capabilities are projected here. */
final class NodeRegistry {
    static final String META_VISIBLE="amin.graph.visible", META_ID="amin.graph.id", META_CAPABILITY="amin.graph.capability_id", META_TITLE="amin.graph.title", META_DESCRIPTION="amin.graph.description", META_PARENT="amin.graph.parent", META_ROUTE="amin.graph.route", META_TYPE="amin.graph.node_type", META_ACTIONS="amin.graph.actions", META_INPUT="amin.graph.input_contract", META_OUTPUT="amin.graph.output_contract", META_VOICE_ENABLED="amin.graph.voice_enabled", META_VOICE_ALIASES="amin.graph.voice_aliases", META_STATUS="amin.graph.status", META_VERSION="amin.graph.version", META_STORAGE_ADAPTER="amin.graph.storage_adapter", META_STORAGE_SOURCE="amin.graph.storage_source_id", META_STORAGE_TABLE="amin.graph.storage_table", META_FILTER_JSON="amin.graph.filter_json", META_INPUT_CONTEXT_JSON="amin.graph.input_context_json";

    static final class Match { final JSONObject node; final String alias; Match(JSONObject node,String alias){this.node=node;this.alias=alias;} }
    private NodeRegistry(){}

    static String navigationJson(Context context){
        JSONArray pages=new JSONArray();
        try{
            PackageManager pm=context.getPackageManager();
            ActivityInfo[] infos=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_ACTIVITIES|PackageManager.GET_META_DATA).activities;
            if(infos!=null)for(ActivityInfo info:infos){Bundle m=info.metaData;if(m==null||!m.getBoolean(META_VISIBLE,false))continue;pages.put(pageFromMetadata(info,m));}
            appendFinanceProjection(pages);
        }catch(Exception ignored){}
        try{return new JSONObject().put("format","amin-app-navigation").put("version",4).put("rootId","app-core").put("rootCapabilityId","app:app-core").put("rootTitle","Amin Pocket").put("rootDescription","Amin Pocket capability root").put("rootRoute","amin-home://open").put("pages",pages).toString();}
        catch(Exception e){return"{\"pages\":[]}";}
    }

    static String registryJson(Context c,NodeMetadataStore o){String b=GraphContract.nodeRegistryJson(navigationJson(c));return o==null?b:o.applyToRegistry(b);}

    static String typedEdgesJson(Context c,NodeMetadataStore o){
        String base=GraphContract.typedEdgesJson(navigationJson(c));
        try{
            JSONObject root=new JSONObject(base);JSONArray edges=root.optJSONArray("edges");if(edges==null){edges=new JSONArray();root.put("edges",edges);}
            edges.put(GraphContract.edge("edge:finance:create:writes-transactions","app:finance-expense-create","app:finance-transactions","writes_to","active","1").put("authority","capability_projection"));
            edges.put(GraphContract.edge("edge:finance:create:opens-transactions-view","app:finance-expense-create","app:finance-transactions-view","opens","active","1").put("authority","capability_projection"));
            edges.put(GraphContract.edge("edge:finance:transactions-view:reads-transactions","app:finance-transactions-view","app:finance-transactions","reads_from","active","1").put("authority","capability_projection"));
            edges.put(GraphContract.edge("edge:finance:food:reads-transactions","app:finance-food","app:finance-transactions","reads_from","active","1").put("authority","capability_projection"));
            base=root.toString();
        }catch(Exception ignored){}
        return o==null?base:o.mergeEdges(base);
    }

    static JSONObject findNode(Context c,NodeMetadataStore o,String id){String n=clean(id);if(n.isEmpty())return null;try{JSONArray a=new JSONObject(registryJson(c,o)).optJSONArray("nodes");if(a==null)return null;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&(n.equals(x.optString("node_id"))||n.equals(x.optString("nodeId"))||n.equals(x.optString("capability_id"))||n.equals(x.optString("capabilityId"))))return x;}}catch(Exception ignored){}return null;}
    static JSONObject findByRoute(Context c,NodeMetadataStore o,String route){String n=clean(route);if(n.isEmpty())return null;try{JSONArray a=new JSONObject(registryJson(c,o)).optJSONArray("nodes");if(a==null)return null;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&n.equals(x.optString("route")))return x;}}catch(Exception ignored){}return null;}

    static Match matchVoice(Context c,NodeMetadataStore o,String transcript){String normalized=VoiceCommandParser.normalize(transcript);if(normalized.isEmpty())return null;Match candidate=null;int best=-1;try{JSONArray nodes=new JSONObject(registryJson(c,o)).optJSONArray("nodes");if(nodes==null)return null;for(int i=0;i<nodes.length();i++){JSONObject n=nodes.optJSONObject(i);if(n==null)continue;JSONObject voice=n.optJSONObject("voice");if(voice==null||!voice.optBoolean("enabled",false))continue;JSONArray aliases=voice.optJSONArray("aliases");if(aliases==null)continue;for(int j=0;j<aliases.length();j++){String alias=aliases.optString(j,"");String a=VoiceCommandParser.normalize(alias);if(a.isEmpty())continue;boolean exact=normalized.equals(a),contains=normalized.contains(a)||a.contains(normalized);if(!exact&&!contains)continue;int score=exact?10000+a.length():a.length();if(score>best){best=score;candidate=new Match(n,alias);}}}}catch(Exception ignored){}return candidate;}

    private static void appendFinanceProjection(JSONArray pages)throws Exception{
        pages.put(virtualPage("finance","finance","財務","個人財務能力群組","control-center","amin-node://finance","group","inspect","","",false,"",new JSONObject(),new JSONObject(),new JSONObject(),"right",4));
        pages.put(virtualPage("finance-expense-create","finance.expense.create","新增支出","新增一筆個人支出紀錄","finance","amin-node://finance-expense-create","capability","create,inspect","transaction_v1","transaction_v1",true,"記帳,新增支出,支出",storage("google_sheets","personal_finance","Transactions"),new JSONObject(),new JSONObject(),"right",1));
        pages.put(virtualPage("finance-transactions-view","finance.expense.dashboard","全部交易","顯示 Transactions 全部交易並可新增資料","finance","amin-data://transactions","data_view","open,create,refresh","transaction_v1","transaction_v1",true,"交易紀錄,全部支出,支出紀錄",storage("google_sheets","personal_finance","Transactions"),new JSONObject(),new JSONObject(),"right",2));
        pages.put(virtualPage("finance-transactions","finance.transactions","Transactions","個人支出交易資料來源","finance","amin-node://finance-transactions","storage","inspect,read","","transaction_v1",false,"",storage("google_sheets","personal_finance","Transactions"),new JSONObject(),new JSONObject(),"bottom",1));
    }

    private static JSONObject virtualPage(String id,String capabilityId,String title,String description,String parent,String route,String type,String actions,String input,String output,boolean voiceEnabled,String aliases,JSONObject storage,JSONObject filter,JSONObject inputContext,String direction,int slot)throws Exception{return new JSONObject().put("id",id).put("capabilityId",capabilityId).put("title",title).put("description",description).put("parent",parent).put("route",route).put("direction",direction).put("slot",slot).put("activity","").put("origin","app").put("locked",true).put("nodeType",type).put("status","active").put("nodeVersion","1").put("actions",csv(actions)).put("inputContract",input).put("outputContract",output).put("voice",new JSONObject().put("enabled",voiceEnabled).put("aliases",csv(aliases))).put("storage",storage).put("filter",filter).put("inputContext",inputContext);}
    private static JSONObject storage(String adapter,String source,String table)throws Exception{return new JSONObject().put("adapter",adapter).put("source_id",source).put("table",table);}

    private static JSONObject pageFromMetadata(ActivityInfo info,Bundle m)throws Exception{JSONObject p=new JSONObject();String graphId=m.getString(META_ID,info.name);String title=m.getString(META_TITLE,info.name.substring(info.name.lastIndexOf('.')+1));p.put("id",graphId);p.put("capabilityId",GraphContract.capabilityId(GraphContract.APP_ORIGIN,graphId,m.getString(META_CAPABILITY,"")));p.put("title",title);p.put("description",m.getString(META_DESCRIPTION,""));p.put("parent",m.getString(META_PARENT,"app-core"));p.put("route",m.getString(META_ROUTE,""));p.put("direction",m.getString("amin.graph.direction","right"));p.put("slot",m.getInt("amin.graph.slot",0));p.put("activity",info.name);p.put("origin","app");p.put("locked",true);p.put("nodeType",m.getString(META_TYPE,"capability"));p.put("status",m.getString(META_STATUS,"active"));p.put("nodeVersion",m.getString(META_VERSION,"1"));p.put("actions",csv(m.getString(META_ACTIONS,"open")));p.put("inputContract",m.getString(META_INPUT,""));p.put("outputContract",m.getString(META_OUTPUT,""));JSONObject voice=new JSONObject();boolean ve=m.containsKey(META_VOICE_ENABLED)?m.getBoolean(META_VOICE_ENABLED,false):!m.getString(META_ROUTE,"").isEmpty();voice.put("enabled",ve);JSONArray aliases=csv(m.getString(META_VOICE_ALIASES,""));if(aliases.length()==0&&ve)aliases.put(title);voice.put("aliases",aliases);p.put("voice",voice);p.put("storage",new JSONObject().put("adapter",m.getString(META_STORAGE_ADAPTER,"")).put("source_id",m.getString(META_STORAGE_SOURCE,"")).put("table",m.getString(META_STORAGE_TABLE,"")));p.put("filter",jsonObject(m.getString(META_FILTER_JSON,"")));p.put("inputContext",jsonObject(m.getString(META_INPUT_CONTEXT_JSON,"")));return p;}
    private static JSONObject jsonObject(String s){try{return clean(s).isEmpty()?new JSONObject():new JSONObject(s);}catch(Exception e){return new JSONObject();}}
    private static JSONArray csv(String value){JSONArray out=new JSONArray();for(String item:split(value))out.put(item);return out;}
    private static List<String> split(String value){List<String> out=new ArrayList<>();if(value==null)return out;for(String item:value.split("[,，|]")){String c=clean(item);if(!c.isEmpty())out.add(c);}return out;}
    private static String clean(String value){return value==null?"":value.trim();}
}
