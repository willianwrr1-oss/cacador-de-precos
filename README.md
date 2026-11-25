# Caçador de Preços

App Android que acompanha o preço de produtos, guarda o histórico e avisa quando o valor cai.

- **Pacote:** `com.willian.cacadorprecos`
- **Compatibilidade:** Android 10 ou superior (minSdk 29, targetSdk 34)
- **Desenvolvido por:** Willian R Rocha
- **Compilação:** GitHub Actions (`.github/workflows/build-apk.yml`)

## Como funciona

- **Duas frentes de pesquisa**
  1. *Fontes cadastradas:* links de lojas para cada produto (o app lê o preço da página).
  2. *Busca em grandes lojas:* procura o nome do produto em Amazon, Mercado Livre, Magazine Luiza, KaBuM!, Americanas, Casas Bahia e Fast Shop. Só essas lojas são consultadas.
- **Intervalo de verificação** nos Ajustes: 15 min, 30 min, 1 h, 3 h, 6 h ou 12 h (WorkManager).
- **Cadastro preciso:** descrição e modelo obrigatórios. O modelo precisa aparecer inteiro no anúncio da loja para o preço valer.
- **Histórico por produto:** leituras do período escolhido no cadastro (7 dias a 1 ano) mais o menor valor já verificado (guardado à parte).
- **Atualização imediata:** botão em cada produto (lista e detalhe) e botão para verificar todos.
- **Notificação:** aviso na barra do celular com vibração de 3 segundos (canal `alertas_preco_v2`).
- **Alerta:** queda em % sobre o último preço, a média ou o menor registrado; preço-alvo; novo menor preço.
- **Dados:** ficam só no aparelho (`filesDir/dados.json`).

## Estrutura

```
.github/workflows/build-apk.yml      workflow que gera o APK
app/precos.jks                       chave de assinatura fixa (não trocar)
app/build.gradle.kts                 versão, dependências, assinatura
app/src/main/java/com/willian/cacadorprecos/
  App.kt, MainActivity.kt            início do app e navegação
  data/   Models, Repo (JSON), Stores (lojas), Fmt (formatação)
  net/    Http, PriceParser (leitura do HTML), PriceFetcher (duas frentes)
  work/   Scheduler (intervalos), PriceCheckWorker, Checker (histórico e alertas), Notifier
  ui/     telas: Home, Detail, Alerts, Sources, Settings, Add, AboutDialog
```

## Ajuste de lojas

Cada loja da busca aberta está em `data/Stores.kt` com seus seletores CSS. Se uma loja mudar o
site e parar de ser lida, a tela **Fontes** mostra "falhou" ao lado dela; é nesse arquivo que se corrige.
