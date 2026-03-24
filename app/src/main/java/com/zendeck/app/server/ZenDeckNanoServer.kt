package com.zendeck.app.server

import android.util.Log
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.service.LinkScraperService
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ZenDeckNanoServer(
    port: Int,
    private val repo: LinkRepository
) : NanoHTTPD(port) {

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    override fun serve(session: IHTTPSession): Response {
        return try {
            // Add CORS headers to every response
            val response = route(session)
            response.addHeader("Access-Control-Allow-Origin", "*")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: ${e.message}")
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal error")
        }
    }

    private fun route(session: IHTTPSession): Response = when {
        session.method == Method.GET  && session.uri == "/"                    -> serveHtml()
        session.method == Method.GET  && session.uri == "/api/links"           -> serveLinks(archived = false)
        session.method == Method.GET  && session.uri == "/api/links/archived"  -> serveLinks(archived = true)
        session.method == Method.GET  && session.uri == "/api/links/all"       -> serveAllLinks()
        session.method == Method.POST && session.uri == "/api/links"           -> ingestLink(session)
        session.method == Method.POST && session.uri == "/api/sync/push"       -> syncPush(session)
        session.method == Method.OPTIONS                                        -> optionsResponse()
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
    }

    private fun serveHtml(): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", HTML_PAGE)

    private fun serveLinks(archived: Boolean): Response {
        val links: List<LinkItem> = runBlocking {
            if (archived) repo.getArchivedLinksSnapshot() else repo.getInboxLinksSnapshot()
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.encodeToString(links))
    }

    private fun ingestLink(session: IHTTPSession): Response {
        val body = HashMap<String, String>()
        session.parseBody(body)
        val postData = body["postData"] ?: body.values.firstOrNull() ?: ""
        val url = try {
            Json.parseToJsonElement(postData).jsonObject["url"]?.jsonPrimitive?.content
        } catch (_: Exception) { null }
            ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing 'url' field"
            )

        val (id, isNew) = runBlocking {
            try {
                val scraped = LinkScraperService.scrape(url)
                repo.addLink(
                    url = url,
                    title = scraped.title.ifBlank { url },
                    description = scraped.description,
                    domain = scraped.domain,
                    faviconUrl = scraped.faviconUrl
                )
            } catch (_: Exception) {
                // Scraping failed — save minimal entry so the URL is at least captured
                val domain = runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")
                repo.addLink(url = url, title = url, description = "", domain = domain, faviconUrl = "")
            }
        }
        val statusCode = if (isNew) Response.Status.CREATED else Response.Status.OK
        return newFixedLengthResponse(statusCode, "application/json", """{"id":"$id","isNew":$isNew}""")
    }

    private fun serveAllLinks(): Response {
        val links: List<LinkItem> = runBlocking { repo.getAllLinks() }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.encodeToString(links))
    }

    /** Receives a JSON array of LinkItems from a peer device and upserts them locally. */
    private fun syncPush(session: IHTTPSession): Response {
        val body = HashMap<String, String>()
        session.parseBody(body)
        val postData = body["postData"] ?: body.values.firstOrNull() ?: ""
        return try {
            val links = Json.decodeFromString<List<LinkItem>>(postData)
            runBlocking { repo.mergeLinksFromPeer(links) }
            Log.i(TAG, "Sync push: received ${links.size} link(s) from peer")
            newFixedLengthResponse(Response.Status.OK, "application/json", """{"synced":${links.size}}""")
        } catch (e: Exception) {
            Log.e(TAG, "Sync push failed: ${e.message}")
            newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid link data: ${e.message}")
        }
    }

    private fun optionsResponse(): Response =
        newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").also {
            it.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            it.addHeader("Access-Control-Allow-Headers", "Content-Type")
        }

    companion object {
        private const val TAG = "ZenDeckNanoServer"
        const val PORT = 7329

        // language=HTML
        val HTML_PAGE: String = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>ZenDeck</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{background:#0d0d0d;color:#e0e0e0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;padding:16px;max-width:700px;margin:0 auto}
h1{color:#00897b;font-size:22px;font-weight:600;margin-bottom:18px;display:flex;align-items:center;gap:8px}
.add-bar{display:flex;gap:8px;margin-bottom:20px}
.add-bar input{flex:1;background:#1a1a1a;border:1px solid #333;color:#e0e0e0;border-radius:10px;padding:11px 14px;font-size:14px;outline:none;transition:border-color .2s}
.add-bar input:focus{border-color:#00897b}
.add-bar button{background:#00897b;color:#fff;border:none;border-radius:10px;padding:11px 20px;cursor:pointer;font-size:14px;font-weight:500;transition:background .2s;white-space:nowrap}
.add-bar button:hover{background:#00695c}
.tabs{display:flex;gap:4px;margin-bottom:16px}
.tab{padding:7px 18px;border-radius:8px;cursor:pointer;font-size:13px;border:1px solid #333;background:none;color:#9e9e9e;transition:all .2s}
.tab.active{background:#00897b22;border-color:#00897b;color:#00897b;font-weight:500}
.card{background:#1a1a1a;border:1px solid #2a5a54;border-radius:12px;padding:13px 15px;margin-bottom:10px;cursor:pointer;transition:border-color .2s,background .2s;text-decoration:none;display:block;color:inherit}
.card:hover{border-color:#00897b;background:#1c2826}
.card-header{display:flex;align-items:center;gap:7px;margin-bottom:7px}
.favicon{width:16px;height:16px;border-radius:50%;flex-shrink:0}
.domain{font-size:12px;color:#9e9e9e;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ttl{font-size:11px;padding:2px 8px;border-radius:4px;white-space:nowrap;flex-shrink:0}
.ttl.fresh{color:#4caf50;background:#0d1f0d}
.ttl.warn{color:#ff9800;background:#1f1500}
.ttl.crit{color:#f44336;background:#1f0505}
.title{font-size:15px;font-weight:500;color:#f0f0f0;margin-bottom:4px;line-height:1.4;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.desc{font-size:13px;color:#9e9e9e;line-height:1.5;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.empty{text-align:center;color:#555;padding:60px 0;font-size:15px}
.status-bar{font-size:11px;color:#555;text-align:center;padding:10px 0 4px}
.toast{position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:#00897b;color:#fff;padding:10px 20px;border-radius:8px;font-size:13px;opacity:0;transition:opacity .3s;pointer-events:none;z-index:999}
.toast.show{opacity:1}
</style>
</head>
<body>
<h1>⚡ ZenDeck</h1>
<div class="add-bar">
  <input id="urlInput" type="url" placeholder="Paste a URL to save to phone…" autocomplete="off">
  <button id="saveBtn" onclick="saveLink()">Save</button>
</div>
<div class="tabs">
  <button class="tab active" onclick="setTab('inbox',this)">Inbox</button>
  <button class="tab" onclick="setTab('archived',this)">Archive</button>
</div>
<div id="links"></div>
<p class="status-bar" id="status">Loading…</p>
<div class="toast" id="toast"></div>
<script>
var currentTab='inbox';
function ttlClass(ms){if(ms<=0||ms/(72*3600000)>=1)return 'crit';if(1-ms/(72*3600000)>=0.5)return 'warn';return 'fresh'}
function fmtTtl(ms){if(ms<=0)return 'Expired';var h=Math.floor(ms/3600000),m=Math.floor(ms%3600000/60000);if(h>=24)return (h/24|0)+'d '+(h%24)+'h';if(h>0)return h+'h '+m+'m';return m+'m'}
function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}
function toast(msg,err){var t=document.getElementById('toast');t.textContent=msg;t.style.background=err?'#c62828':'#00897b';t.classList.add('show');setTimeout(function(){t.classList.remove('show')},2500)}
function render(links){
  var now=Date.now(),el=document.getElementById('links');
  if(!links.length){el.innerHTML='<p class="empty">Nothing here ✓</p>';return}
  el.innerHTML=links.map(function(l){
    var rem=l.expiresAt-now;
    return '<a class="card" href="'+esc(l.url)+'" target="_blank" rel="noopener noreferrer">'+
      '<div class="card-header">'+
      '<img class="favicon" src="'+esc(l.faviconUrl)+'" onerror="this.style.display=\'none\'" loading="lazy">'+
      '<span class="domain">'+esc(l.domain||new URL(l.url).hostname)+'</span>'+
      '<span class="ttl '+ttlClass(rem)+'">'+fmtTtl(rem)+'</span>'+
      '</div>'+
      '<div class="title">'+esc(l.title||l.url)+'</div>'+
      (l.description?'<div class="desc">'+esc(l.description)+'</div>':'')+
      '</a>';
  }).join('');
}
function load(){
  var path=currentTab==='inbox'?'/api/links':'/api/links/archived';
  fetch(path).then(function(r){return r.json()}).then(function(d){
    render(d);
    document.getElementById('status').textContent='Updated '+new Date().toLocaleTimeString()+' · '+d.length+' link(s)';
  }).catch(function(){document.getElementById('status').textContent='Connection error'});
}
function setTab(tab,btn){
  currentTab=tab;
  document.querySelectorAll('.tab').forEach(function(t){t.classList.remove('active')});
  btn.classList.add('active');
  load();
}
function saveLink(){
  var url=document.getElementById('urlInput').value.trim();
  if(!url)return;
  var btn=document.getElementById('saveBtn');
  btn.disabled=true;btn.textContent='Saving…';
  fetch('/api/links',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({url:url})})
    .then(function(r){
      if(!r.ok)throw new Error('HTTP '+r.status);
      return r.json();
    })
    .then(function(d){
      document.getElementById('urlInput').value='';
      toast(d.isNew?'Saved to ZenDeck ✓':'Already in ZenDeck');
      if(currentTab==='inbox')load();
    })
    .catch(function(){toast('Failed to save link',true)})
    .finally(function(){btn.disabled=false;btn.textContent='Save'});
}
document.getElementById('urlInput').addEventListener('keydown',function(e){if(e.key==='Enter')saveLink()});
load();
setInterval(load,30000);
</script>
</body>
</html>"""
    }
}
