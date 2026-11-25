package com.willian.cacadorprecos.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willian.cacadorprecos.BuildConfig

private const val EMAIL = "willian.wrr1@gmail.com"

/** Tela "Sobre" com as informações do desenvolvedor, no mesmo estilo dos outros apps. */
@Composable
fun AboutDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFFE0E7EF),
        title = { Text("Sobre", fontSize = 24.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Caçador de Preços", color = Pal.Teal, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Acompanha o preço dos produtos que você cadastra, guarda o histórico das leituras e avisa quando o valor cai. Busca nas lojas dos links cadastrados e em grandes varejistas.",
                    fontSize = 15.sp, lineHeight = 22.sp, color = Pal.FlatFg
                )
                Spacer(Modifier.height(14.dp))
                Divisor()
                InfoRow("Versão", BuildConfig.VERSION_NAME)
                InfoRow("Última atualização", BuildConfig.BUILD_DATE)
                InfoRow("Compatibilidade", "Android 10 ou superior")
                Divisor()
                InfoRow("Desenvolvido por", "Willian R Rocha")
                InfoRow("Contato", EMAIL)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + EMAIL))
                            .putExtra(Intent.EXTRA_SUBJECT, "Caçador de Preços")
                        try {
                            ctx.startActivity(i)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Nenhum aplicativo de e-mail encontrado", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = CircleShape
                ) {
                    Text("Enviar e-mail", color = Pal.Teal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(16.dp))
                Divisor()
                Spacer(Modifier.height(12.dp))
                Text("Proteção de dados", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "O aplicativo não coleta nem envia dados pessoais. A internet é usada apenas para consultar o preço dos produtos que você cadastrou nas lojas. Produtos, histórico e preferências ficam somente neste aparelho.",
                    fontSize = 14.sp, lineHeight = 21.sp, color = Pal.FlatFg
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Fechar", color = Pal.Teal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    )
}

@Composable
private fun Divisor() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFC3CBD6))
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, Modifier.weight(0.42f), fontSize = 15.sp, color = Pal.FlatFg)
        Text(value, Modifier.weight(0.58f), fontSize = 15.sp)
    }
}
