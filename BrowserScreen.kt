package com.mysql.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private val SNIPPETS = listOf(
    "SELECT * FROM " to "SELECT",
    "SELECT * FROM tabla LIMIT 50;" to "SELECT 50",
    "SHOW TABLES;" to "TABLES",
    "SHOW PROCESSLIST;" to "PROCESOS",
    "SHOW CREATE TABLE " to "CREATE",
    "DESCRIBE " to "DESCRIBE",
    "SELECT COUNT(*) FROM " to "COUNT",
    "INSERT INTO tabla (col) VALUES ('x');" to "INSERT",
    "UPDATE tabla SET col='x' WHERE id=1;" to "UPDATE",
    "DELETE FROM tabla WHERE id=1;" to "DELETE",
    " WHERE " to "WHERE",
    " ORDER BY id DESC LIMIT 20;" to "ORDER"
)

private const val MOBILE_JS = """
(function(){
 var m=document.querySelector('meta[name=viewport]');
 if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}
 m.content='width=device-width, initial-scale=0.9';
 if(!document.getElementById('mysql-app-css')){
  var s=document.createElement('style');s.id='mysql-app-css';
  s.textContent='input[type=submit],button,.button{min-height:40px}input,select,textarea{font-size:16px!important}#sqlquery{min-height:160px}';
  document.head.appendChild(s);
 }
})();
"""

private fun darkJs(on: Boolean) = """
(function(){
 var id='mysql-app-dark',e=document.getElementById(id);
 if(e)e.remove();
 if($on){var s=document.createElement('style');s.id=id;
  s.textContent='html{filter:invert(1) hue-rotate(180deg)}img,video,canvas{filter:invert(1) hue-rotate(180deg)}';
  document.head.appendChild(s);}
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(server: Server, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var dark by remember { mutableStateOf(false) }
    var snippets by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var loginTries by remember { mutableIntStateOf(0) }
    var chooserCb by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        chooserCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        chooserCb = null
    }

    BackHandler {
        if (web?.canGoBack() == true) web?.goBack() else onExit()
    }

    fun insertSnippet(text: String) {
        val t = JSONObject.quote(text)
        web?.evaluateJavascript(
            """(function(){var t=$t;var c=document.querySelector('.CodeMirror');
            if(c&&c.CodeMirror){c.CodeMirror.replaceSelection(t);c.CodeMirror.focus();return;}
            var a=document.getElementById('sqlquery');if(a){a.value+=t;a.focus();}})();""", null
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopAppBar(
            title = { Text(server.name, maxLines = 1) },
            navigationIcon = { TextButton(onClick = onExit) { Text("‹ Salir") } },
            actions = {
                TextButton(onClick = { web?.reload() }) { Text("↻") }
                TextButton(onClick = {
                    dark = !dark
                    web?.evaluateJavascript(darkJs(dark), null)
                }) { Text(if (dark) "☀" else "☾") }
                TextButton(onClick = { snippets = !snippets }) { Text("SQL") }
            }
        )
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    CookieManager.getInstance().setAcceptCookie(true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript(MOBILE_JS, null)
                            view.evaluateJavascript(darkJs(dark), null)
                            if (server.autoLogin && server.user.isNotEmpty() && loginTries < 2) {
                                val u = JSONObject.quote(server.user)
                                val p = JSONObject.quote(server.pass)
                                view.evaluateJavascript(
                                    """(function(){var u=document.querySelector('input[name=pma_username]'),
                                    p=document.querySelector('input[name=pma_password]'),
                                    f=document.getElementById('login_form');
                                    if(u&&p&&f){u.value=$u;p.value=$p;f.submit();return 1;}return 0;})();"""
                                ) { if (it == "1") loginTries++ }
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }

                        override fun onShowFileChooser(
                            v: WebView?, cb: ValueCallback<Array<Uri>>,
                            params: FileChooserParams
                        ): Boolean {
                            chooserCb?.onReceiveValue(null)
                            chooserCb = cb
                            chooser.launch(params.createIntent())
                            return true
                        }
                    }
                    setDownloadListener { url, ua, cd, mime, _ ->
                        val name = URLUtil.guessFileName(url, cd, mime)
                        val req = DownloadManager.Request(Uri.parse(url)).apply {
                            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                            addRequestHeader("User-Agent", ua)
                            setMimeType(mime)
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                        }
                        (c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                        Toast.makeText(c, "Descargando $name", Toast.LENGTH_SHORT).show()
                    }
                    loadUrl(server.url)
                    web = this
                }
            },
            onRelease = { it.destroy() }
        )

        if (snippets) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SNIPPETS.forEach { (sql, label) ->
                    AssistChip(onClick = { insertSnippet(sql) }, label = { Text(label) })
                }
            }
        }
    }
}
