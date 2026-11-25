package com.willian.cacadorprecos.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willian.cacadorprecos.data.AppState
import com.willian.cacadorprecos.data.Product
import com.willian.cacadorprecos.data.ProductLink
import com.willian.cacadorprecos.data.Repo
import com.willian.cacadorprecos.data.Stores
import com.willian.cacadorprecos.net.PriceParser
import com.willian.cacadorprecos.work.Scheduler

@Composable
fun AddScreen(state: AppState, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var nome by rememberSaveable { mutableStateOf("") }
    var modelo by rememberSaveable { mutableStateOf("") }
    var guardar by rememberSaveable { mutableStateOf(30) }
    var categoria by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var alvo by rememberSaveable { mutableStateOf("") }
    var pct by rememberSaveable { mutableStateOf(state.settings.defaultPct) }
    var reg by rememberSaveable { mutableStateOf(true) }
    var aberta by rememberSaveable { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    val forma = RoundedCornerShape(12.dp)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(
            title = "Novo produto",
            subtitle = "O app procura o preço assim que você salvar.",
            leading = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
            }
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "A descrição e o modelo são o que deixa a busca precisa: o modelo precisa aparecer no anúncio da loja para o preço valer.",
                fontSize = 13.sp, color = Pal.FlatFg, lineHeight = 19.sp
            )
            OutlinedTextField(
                value = nome, onValueChange = { nome = it; erro = null },
                modifier = Modifier.fillMaxWidth(), shape = forma,
                label = { Text("Descrição do produto") },
                placeholder = { Text("Ex.: Fone de ouvido sem fio Sony com cancelamento de ruído") },
                minLines = 2, maxLines = 3
            )
            OutlinedTextField(
                value = modelo, onValueChange = { modelo = it; erro = null },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = forma,
                label = { Text("Modelo") },
                placeholder = { Text("Ex.: WH-1000XM5") }
            )
            OutlinedTextField(
                value = categoria, onValueChange = { categoria = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = forma,
                label = { Text("Categoria (opcional)") }
            )
            OutlinedTextField(
                value = link, onValueChange = { link = it; erro = null },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = forma,
                label = { Text("Link de uma loja (opcional)") },
                placeholder = { Text("Cole o endereço da página do produto") }
            )
            OutlinedTextField(
                value = alvo, onValueChange = { alvo = it; erro = null },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = forma,
                label = { Text("Preço-alvo em R$ (opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Avisar quando cair", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Stepper(
                    pct.toString() + "%",
                    onMinus = { pct = (pct - 1).coerceAtLeast(1) },
                    onPlus = { pct = (pct + 1).coerceAtMost(50) }
                )
            }
            AppCard {
                Text("Guardar o histórico por", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Tempo em que as leituras ficam guardadas para comparar preços. O menor valor já visto fica guardado à parte.",
                    fontSize = 12.sp, color = Pal.Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                KeepPicker(guardar) { guardar = it }
            }
            AppCard(padding = 16.dp) {
                SwitchRow("Fontes cadastradas", "Lê o link informado acima", reg, { reg = it })
                Rule()
                SwitchRow("Busca em grandes lojas", "Só varejistas de grande porte", aberta, { aberta = it })
            }
            val msg = erro
            if (msg != null) Text(msg, color = Pal.Danger, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(48.dp), shape = forma
                ) { Text("Cancelar", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        val n = nome.trim()
                        val m = modelo.trim()
                        val l = link.trim()
                        val a = if (alvo.isBlank()) null else PriceParser.parseMoney(alvo)
                        if (n.isEmpty()) {
                            erro = "Informe a descrição do produto."
                        } else if (m.isEmpty()) {
                            erro = "Informe o modelo do produto. Ele garante que a busca ache o item certo."
                        } else if (l.isNotEmpty() && !(l.startsWith("http://") || l.startsWith("https://"))) {
                            erro = "O link precisa começar com https://"
                        } else if (alvo.isNotBlank() && a == null) {
                            erro = "Preço-alvo inválido. Exemplo: 1.599,00"
                        } else {
                            val p = Product(
                                id = Repo.newId(),
                                name = n,
                                model = m,
                                keepDays = guardar,
                                category = categoria.trim(),
                                targetPrice = a,
                                alertPct = pct,
                                searchRegistered = reg,
                                searchOpen = aberta,
                                links = if (l.isEmpty()) emptyList() else listOf(ProductLink(Stores.nameFor(l), l))
                            )
                            Repo.addProduct(p)
                            Scheduler.runOne(ctx, p.id)
                            Toast.makeText(ctx, "Produto cadastrado. Buscando preços…", Toast.LENGTH_SHORT).show()
                            onSaved()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp), shape = forma,
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Teal, contentColor = Color.White)
                ) { Text("Salvar", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
