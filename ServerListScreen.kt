package com.mysql.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(store: ServerStore, onOpen: (Server) -> Unit) {
    var servers by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<Server?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var lock by remember { mutableStateOf(store.lockEnabled) }

    fun persist(list: List<Server>) {
        servers = list
        store.save(list)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.logo), null, Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("MySQL")
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showDialog = true }) {
                Icon(Icons.Default.Add, "Añadir")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bloquear app con huella/PIN", Modifier.weight(1f))
                Switch(checked = lock, onCheckedChange = { lock = it; store.lockEnabled = it })
            }
            Spacer(Modifier.height(8.dp))
            if (servers.isEmpty()) {
                Text("Toca + para añadir tu phpMyAdmin (ej: https://pma.tudominio.com)")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(servers, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(s) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                Text(s.url, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { editing = s; showDialog = true }) {
                                Icon(Icons.Default.Edit, "Editar")
                            }
                            IconButton(onClick = { persist(servers.filter { it.id != s.id }) }) {
                                Icon(Icons.Default.Delete, "Borrar")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        val e = editing
        var name by remember { mutableStateOf(e?.name ?: "") }
        var url by remember { mutableStateOf(e?.url ?: "") }
        var user by remember { mutableStateOf(e?.user ?: "") }
        var pass by remember { mutableStateOf(e?.pass ?: "") }
        var auto by remember { mutableStateOf(e?.autoLogin ?: true) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (e == null) "Nuevo servidor" else "Editar servidor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text("URL de phpMyAdmin") }, singleLine = true)
                    OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true)
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto-login", Modifier.weight(1f))
                        Switch(auto, { auto = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val fixed = if (url.startsWith("http")) url.trim() else "https://${url.trim()}"
                    val item = Server(
                        e?.id ?: UUID.randomUUID().toString(),
                        name.ifBlank { fixed }, fixed, user, pass, auto
                    )
                    persist(if (e == null) servers + item else servers.map { if (it.id == e.id) item else it })
                    showDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancelar") } }
        )
    }
}
