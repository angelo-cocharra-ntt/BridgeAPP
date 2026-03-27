# BridgeAPP Deep Link — Configuração da Canvas App (Power Apps)

## Como funciona

```
[Canvas App — botão "Atualizar"]
  └── Launch("bridgeappb://getsensors?returnUrl=[URL da Canvas App]")
        │
        ▼ Chrome abre App B (flash rápido ~300ms)
        │
[BridgeAppB_DeepLink APK]
  └── Gera leitura de sensores
  └── Abre: [URL da Canvas App]?temperature=24.5&humidity=65.0&pressure=1013.0
        │
        ▼ Chrome regressa à Canvas App com dados no URL
        │
[Canvas App]
  └── Param("temperature") → "24.5"
  └── Param("humidity")    → "65.0"
  └── Param("pressure")    → "1013.0"
```

**Nota:** Cada leitura recarrega a Canvas App — este é o comportamento esperado do Deep Link.

---

## Passo 1 — Instalar o APK

1. Vai a **[github.com/angelo-cocharra-ntt/BridgeAPP/actions](https://github.com/angelo-cocharra-ntt/BridgeAPP/actions)**
2. Abre o workflow **"Build BridgeAppB DeepLink APK"**
3. Descarrega o artefacto **`BridgeAppB-deeplink-apk`**
4. Instala no Android (ativa "Fontes desconhecidas" se necessário)

---

## Passo 2 — Criar a Canvas App no Power Apps

1. Vai a **[make.powerapps.com](https://make.powerapps.com)**
2. Cria uma nova **Canvas App** (em branco, layout Telemóvel)
3. Dá-lhe o nome: `SensorDashboard — DeepLink`

---

## Passo 3 — Obter o URL da Canvas App

**Antes de configurar o botão**, precisas do URL de execução da Canvas App.

1. Guarda e publica a Canvas App (botão **Publicar** no canto superior)
2. Clica em **Ficheiro → Detalhes**
3. Copia o **Ligação Web** (algo como `https://apps.powerapps.com/play/e/[env-id]/a/[app-id]?tenantId=[tenant-id]`)

---

## Passo 4 — Configurar o Botão

Insere um **Botão** na Canvas App e define a propriedade `OnSelect`:

```
Launch(
    "bridgeappb://getsensors?returnUrl=" &
    EncodeUrl("https://apps.powerapps.com/play/e/[env-id]/a/[app-id]?tenantId=[tenant-id]")
)
```

> Substitui o URL pelo que copiaste no Passo 3.

**Texto do botão:** `"🔄 Atualizar Sensores"`

---

## Passo 5 — Configurar os Labels de dados

Insere 3 **Labels** (rótulos de texto) com estas fórmulas:

| Label | Propriedade `Text` | Exemplo de resultado |
|---|---|---|
| Temperatura | `If(IsBlank(Param("temperature")), "—", Param("temperature") & " °C")` | `24.5 °C` |
| Humidade | `If(IsBlank(Param("humidity")), "—", Param("humidity") & " %")` | `65.0 %` |
| Pressão | `If(IsBlank(Param("pressure")), "—", Param("pressure") & " hPa")` | `1013.0 hPa` |

> Na primeira abertura da app (sem deep link), os Params estão vazios — o `If(IsBlank(...))` mostra `—` em vez de um valor em branco.

---

## Passo 6 — Configurar o Label de última atualização (opcional)

Insere um Label adicional:

```
If(
    IsBlank(Param("updatedAt")),
    "Sem dados — prime Atualizar",
    "Última leitura: " & Text(DateAdd(Date(1970,1,1), Value(Param("updatedAt"))/1000/86400, TimeUnit.Days), "dd/mm/yyyy hh:mm:ss")
)
```

---

## Passo 7 — Testar

1. Abre a Canvas App no **Chrome do Android** (via [make.powerapps.com](https://make.powerapps.com))
2. Clica em **▶ Play**
3. Clica no botão **"🔄 Atualizar Sensores"**
4. Chrome abre App B brevemente (~300ms flash) → regressa com os dados
5. Os valores de temperatura, humidade e pressão aparecem nos labels

---

## Diferenças face à versão HTTP Server

| Critério | HTTP Server (POC original) | Deep Link (esta POC) |
|---|---|---|
| Flash visível de App B | ❌ Nenhum | ⚠️ ~300ms ao atualizar |
| Auto-refresh contínuo | ✅ A cada 10s automático | ❌ Manual (botão) |
| App B em background | ForegroundService permanente | Abre e fecha instantaneamente |
| Recarrega Power Apps | ❌ Não recarrega | ✅ Recarrega a cada leitura |
| Permissões Android necessárias | INTERNET, FOREGROUND_SERVICE | Nenhuma permissão especial |
| Sem ForegroundService | ❌ Precisa | ✅ Não precisa |
| Funciona 100% offline | ✅ | ✅ |
