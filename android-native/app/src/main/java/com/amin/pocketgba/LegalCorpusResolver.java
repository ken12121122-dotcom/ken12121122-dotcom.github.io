package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only grounding source for startup/equity/company-law questions, built from bundled
 * official law text (公司法／商業登記法／證券交易法／中小企業發展條例；see
 * {@code assets/legal-corpus/*.json}, sourced from law.moj.gov.tw via a structural JSON
 * conversion — content unmodified, each article keeps its official source URL).
 *
 * Same shape as the web prototype this was ported from: deterministic keyword gate decides
 * whether a question is in scope, a small bigram search picks the most relevant articles, and a
 * strict system prompt forces citation-or-refusal — never a claim from the model's own memory.
 */
final class LegalCorpusResolver {
    private static final String[] ASSET_FILES = {
        "legal-corpus/company-act.json",
        "legal-corpus/business-registration-act.json",
        "legal-corpus/securities-exchange-act.json",
        "legal-corpus/sme-development-act.json",
    };
    private static final int MAX_ARTICLES = 6;
    private static final int MAX_CONTEXT_CHARS = 6000;

    private static final String[] KEYWORDS = {
        "公司法", "股權", "股份", "增資", "董事", "股東會", "商業登記",
        "證券交易", "上市", "上櫃", "員工認股", "中小企業", "新創",
        "創業", "設立公司", "有限公司", "股份有限公司", "出資",
    };

    private static final class Article {
        final String lawName;
        final String officialUrl;
        final String version;
        final String number;
        final String text;

        Article(String lawName, String officialUrl, String version, String number, String text) {
            this.lawName = lawName;
            this.officialUrl = officialUrl;
            this.version = version;
            this.number = number;
            this.text = text;
        }
    }

    /** Loaded once per process; the bundled corpus never changes at runtime. */
    private static List<Article> cache;

    private LegalCorpusResolver() { }

    static boolean isLegalQuestion(String raw) {
        String query = VoiceCommandParser.normalize(raw);
        if (query.isEmpty()) return false;
        for (String keyword : KEYWORDS) {
            if (query.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * @return a context object with {@code evidence} (matched article text), {@code citations}
     *         (structured law/article/url/version records) and {@code has_evidence}.
     */
    static JSONObject buildContext(Context context, String query) {
        List<Article> hits = search(load(context), query);
        JSONArray citations = new JSONArray();
        StringBuilder evidence = new StringBuilder();
        for (Article article : hits) {
            if (evidence.length() > 0) evidence.append("\n\n");
            evidence.append("【").append(article.lawName).append(" 第 ").append(article.number)
                    .append(" 條】\n").append(article.text);
            try {
                citations.put(new JSONObject()
                        .put("law_name", article.lawName)
                        .put("article", article.number)
                        .put("official_url", article.officialUrl)
                        .put("version", article.version));
            } catch (Exception ignored) { }
        }
        try {
            return new JSONObject()
                    .put("evidence", evidence.toString())
                    .put("citations", citations)
                    .put("has_evidence", !hits.isEmpty());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    static String systemPrompt(JSONObject context) {
        String base = "你是狐狸，一個只依照已核准法規條文回答的創業／股權法規查詢助理。"
                + "規則：只能依照下方條文證據回答，不得使用你自己的法律知識或記憶補充；"
                + "每個法規主張都必須在文字中註明法規名稱與條號，例如「（公司法第20條）」；"
                + "沒有足夠條文證據時要明確說「查無足夠條文證據，建議人工查證」，不得硬答；"
                + "不得宣稱任何最終法規符合性判定，重大決策一律提醒使用者諮詢專業意見或人工查證正式法規全文。";
        if (context == null || !context.optBoolean("has_evidence", false)) {
            return base + "\n\n這次沒有檢索到相關條文，請依規則明確告知查無證據。";
        }
        return base + "\n\n條文證據：\n" + context.optString("evidence", "");
    }

    private static synchronized List<Article> load(Context context) {
        if (cache != null) return cache;
        List<Article> out = new ArrayList<>();
        for (String path : ASSET_FILES) {
            try (InputStream input = context.getAssets().open(path)) {
                JSONObject law = new JSONObject(readStream(input));
                String name = law.optString("name", "");
                String url = law.optString("official_url", "");
                String version = law.optString("version", "");
                JSONArray articles = law.optJSONArray("articles");
                if (articles == null) continue;
                for (int i = 0; i < articles.length(); i++) {
                    JSONObject article = articles.optJSONObject(i);
                    if (article == null) continue;
                    String number = article.optString("number", "");
                    String text = article.optString("text", "");
                    if (!text.isEmpty()) out.add(new Article(name, url, version, number, text));
                }
            } catch (Exception ignored) {
                // Missing/unreadable asset for one law degrades to "no evidence" for it,
                // never to a fabricated answer.
            }
        }
        cache = out;
        return cache;
    }

    private static List<Article> search(List<Article> all, String rawQuery) {
        List<Article> out = new ArrayList<>();
        String query = VoiceCommandParser.normalize(rawQuery);
        if (query.length() < 2) return out;
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            String haystack = VoiceCommandParser.normalize(all.get(i).text);
            int score = 0;
            for (int j = 0; j + 1 < query.length(); j++) {
                if (haystack.contains(query.substring(j, j + 2))) score++;
            }
            if (score > 0) scored.add(new int[]{i, score});
        }
        scored.sort((a, b) -> b[1] - a[1]);
        int chars = 0;
        for (int[] entry : scored) {
            if (out.size() >= MAX_ARTICLES || chars > MAX_CONTEXT_CHARS) break;
            Article article = all.get(entry[0]);
            out.add(article);
            chars += article.text.length();
        }
        return out;
    }

    private static String readStream(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
