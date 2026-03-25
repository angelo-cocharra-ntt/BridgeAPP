# Guia de Configuração — App A (Power Apps Canvas)

> Este guia descreve os passos para criar a App A que consome os dados servidos pela App B (BridgeAppB) via HTTP local.

---

## Pré-requisitos

- App B instalada e a correr no tablet Android (BridgeAppB.apk)
- Acesso a [make.powerapps.com](https://make.powerapps.com)
- Power Apps Mobile instalado no **mesmo** tablet Android

---

## Passo 1 — Criar a Canvas App

1. Aceder a [make.powerapps.com](https://make.powerapps.com)
2. Clicar em **+ Create** → **Blank app** → **Blank canvas app**
3. Nome: `SensorDashboard`
4. Formato: **Tablet** (paisagem)
5. Clicar em **Create**

---

## Passo 2 — Adicionar o WebBrowser Control

1. No ecrã principal, ir ao menu **Insert** (lado esquerdo)
2. Pesquisar `Web browser` e inserir o controlo
3. Redimensionar para ocupar o ecrã completo (ou a área desejada)

**Configurar as propriedades do WebBrowser control:**

| Propriedade | Valor |
|---|---|
| `URL` | `"http://localhost:8080/sensors"` |
| `Width` | `Parent.Width` |
| `Height` | `Parent.Height` |
| `X` | `0` |
| `Y` | `0` |

> **Nota:** O WebBrowser control do Power Apps usa o WebView nativo do Android. Pedidos para `localhost` ficam no dispositivo — não passam pela cloud Microsoft. Isto é o que torna a comunicação 100% offline.

---

## Passo 3 — (Opcional) Botão de Refresh

Para dar ao utilizador a capacidade de forçar uma actualização:

1. Inserir um **Button** control
2. Propriedade `Text`: `"↻ Actualizar"`
3. Propriedade `OnSelect`:
   ```
   Refresh(WebBrowser1)
   ```
4. Posicionar discretamente no canto inferior direito

> O auto-refresh automático a cada 10 segundos já está configurado na App B (`<meta http-equiv="refresh" content="10">`), por isso o botão é opcional.

---

## Passo 4 — Publicar e Instalar no Tablet

1. Clicar em **File** → **Save** → **Publish**
2. No tablet Android, abrir a **Power Apps Mobile** app
3. Fazer login com a mesma conta Microsoft 365
4. A app `SensorDashboard` aparece na lista → abrir

---

## Passo 5 — Verificar o Fluxo Completo

**Order de arranque (uma só vez, após setup):**

1. Ligar o tablet → App B arranca automaticamente (BootReceiver)
2. Abrir Power Apps Mobile → abrir `SensorDashboard`
3. O WebBrowser carrega `http://localhost:8080/sensors`
4. Os dados dos sensores aparecem — actualizados a cada 10 segundos

**Testar offline:**

1. Activar **Modo Avião** no tablet
2. Reabrir ou fazer refresh na SensorDashboard
3. Os dados continuam a ser servidos pela App B (Ktor é local, sem internet)
4. ✅ 100% offline funcional

---

## Arquitectura de referência rápida

```
[Tablet Android]
  ├── Power Apps Mobile (App A)
  │     └── WebBrowser control → http://localhost:8080/sensors
  │                                         │
  └── BridgeAppB (App B) — background      │
        └── SensorServerService             │
              └── Ktor @ localhost:8080  ←──┘
                    └── GET /sensors → HTML com dados
```

---

## Troubleshooting

| Problema | Causa provável | Solução |
|---|---|---|
| WebBrowser mostra página em branco | App B não está a correr | Abrir BridgeAppB e pressionar "Iniciar Servidor" |
| "ERR_CLEARTEXT_NOT_PERMITTED" | network_security_config não aplicado | Verificar AndroidManifest.xml |
| Dados não actualizam | Normal — aguardar 10s / pressionar Refresh | — |
| App B não arranca com o tablet | Permissão BOOT_COMPLETED negada | Nas definições do Android, verificar permissões da BridgeAppB |
