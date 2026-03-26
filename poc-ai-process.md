# POC BridgeAPP — Processo de Criação com AI

> Este documento regista, em tempo real, todas as interações com AI que guiaram a criação desta POC.
> O objectivo é demonstrar como a AI pode ser usada como co-piloto de engenharia desde a primeira decisão arquitectural.

---

## Contexto

**Data de início:** 25 de Março de 2026  
**Objectivo:** Criar uma POC com duas aplicações que comunicam entre si offline:
- **App A** — Power Apps Canvas App (frontend, experiência do utilizador)
- **App B** — Aplicação Android nativa em Kotlin (fornecedor de dados, invisível ao utilizador)

**Requisito chave:** O utilizador da App A não deve perceber que a App A está a obter dados da App B.

---

## Interação 1 — Definição inicial e levantamento de contexto

**Data:** 25 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6)

### Prompt inicial
> *"Eu quero fazer uma POC, criar uma aplicação A (em Power Apps) e criar uma APP B para Android (Kotlin). A app A tem de comunicar com a APP B para ir buscar dados, isto tem de funcionar em offline..."*

### Perguntas feitas pela AI para recolher contexto:
A AI realizou um questionário estruturado para perceber os requisitos:

| Pergunta | Resposta |
|---|---|
| Tipo de dados que App B fornece | Dados de sensores/IoT |
| Cenário de rede | Mesmo dispositivo (tablet Android) |
| Tipo de Power App | Canvas App |
| Quando criar documentação | Desde o início, actualizar ao longo do processo |
| Infraestrutura cloud | Microsoft 365 / Power Platform disponível |
| Dados concretos | Dados simulados genéricos (para demo técnica) |
| Grau de offline | 100% offline — sem internet de todo |

### Proposta inicial da AI
A AI propôs usar **SharePoint List** como data store partilhado:
- App B enviaria dados para SharePoint REST API (com MSAL)
- App A leria do SharePoint connector nativo do Power Apps

### Decisão
❌ **Proposta rejeitada pelo utilizador** — não queria depender de SharePoint nem de qualquer serviço cloud.

### Aprendizagem registada
A AI identificou e documentou um constraint técnico crítico:
> *Power Apps Canvas App não consegue comunicar directamente com localhost/serviços locais no mesmo dispositivo. Todos os conectores são roteados pela infraestrutura Azure da Microsoft. Consequência: a abordagem cloud-first foi afastada pela AI antes de ser implementada, poupando tempo de desenvolvimento.*

---

## Interação 2 — Exploração de alternativas de comunicação offline

**Data:** 25 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6)

### Prompt
> *"Eu não quero SharePoint nenhum, eu quero ter 2 APPs que comunicam entre si, offline. Temos opções como deeplink? SDK? alguma coisa?"*

### Análise da AI — Opções exploradas

#### Opção 1: Deep Link Round-Trip
- App A faz `Launch("bridgeappb://getdata")` → abre App B temporariamente
- App B processa e devolve via deep link: `Launch("ms-apps://..." + params)`
- App A usa `Param("temperature")` para ler valores

**Avaliação:**
- ✅ 100% offline
- ❌ Flash visível de troca de app (~200-500ms)
- ❌ Dados limitados pelo tamanho do URL
- ❌ Não satisfaz o requisito "utilizador não repara"

#### Opção 2: Local HTTP Server ⭐ (escolhida)
- App B corre um servidor **Ktor** em `localhost:8080` como `ForegroundService` permanente
- App A usa o controlo **WebBrowser** do Power Apps a apontar para `http://localhost:8080/sensors`
- O WebBrowser usa o WebView nativo do Android → pedidos a `localhost` ficam no dispositivo, não passam pela cloud

**Avaliação:**
- ✅ 100% offline
- ✅ UX completamente transparente — utilizador nunca vê App B
- ✅ Sem limite de tamanho de dados
- ✅ Dados actualizam em tempo real (auto-refresh via `<meta>` tag)
- ⚠️ App B precisa de estar a correr em background (ForegroundService)

#### Opção 3: Híbrido
- Combinação de Deep Link + HTTP — mais complexo, sem vantagem sobre Opção 2

### Decisão
✅ **Opção 2 escolhida pelo utilizador** — Local HTTP Server com Ktor.

---

## Interação 3 — Implementação App B (Kotlin + Ktor)

**Data:** 25 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6) — modo Agent

### Prompt
> *"Podemos avançar"*

### Plano de implementação definido pela AI

#### Arquitectura final

```
[App A — Power Apps Canvas]
  └── Componente PCF (SensorViewer) → iframe → http://localhost:8080/sensors
                                                          │ HTTP local, no dispositivo
[App B — Kotlin Android]  (invisível para o utilizador)
  └── ForegroundService (background, silencioso)
        └── Ktor HTTP Server @ localhost:8080
              ├── GET /sensors      → HTML+CSS dashboard (visto pelo utilizador via App A)
              ├── GET /api/sensors  → JSON (extensibilidade futura)
              └── GET /health       → "OK" (verificação rápida)
              └── SensorDataGenerator (gera leituras a cada 5s em coroutine)
```

#### Considerações técnicas críticas documentadas pela AI
1. `android:usesCleartextTraffic="true"` + `network_security_config.xml` — Android 9+ bloqueia HTTP por defeito; só `localhost` e `127.0.0.1` são permitidos
2. `ForegroundService` exige notificação persistente (requisito mandatório Android 8+); configurada com `IMPORTANCE_MIN` para ser discreta
3. `foregroundServiceType="dataSync"` obrigatório no Android 14+
4. `RECEIVE_BOOT_COMPLETED` para App B arrancar automaticamente com o tablet
5. `START_STICKY` no `onStartCommand` garante restart automático se o SO terminar o serviço

#### Stack tecnológica
| Componente | Tecnologia |
|---|---|
| App A | Power Apps Canvas App + PCF Component |
| App A — bridge | TypeScript iframe (PCF) |
| App B — servidor | Ktor 2.3.11 (ktor-server-netty) |
| App B — HTML | ktor-server-html-builder |
| App B — background | Android ForegroundService |
| App B — auto-start | BroadcastReceiver (BOOT_COMPLETED) |
| App B — async | Kotlin Coroutines + SupervisorJob |
| Comunicação | HTTP local (localhost:8080) |

#### Ficheiros implementados — App B
- ✅ `BridgeAppB/settings.gradle.kts`
- ✅ `BridgeAppB/build.gradle.kts`
- ✅ `BridgeAppB/gradle/libs.versions.toml`
- ✅ `BridgeAppB/app/build.gradle.kts`
- ✅ `SensorReading.kt` — data class com helpers de cor e status para o HTML
- ✅ `SensorDataGenerator.kt` — gera dados com variação sinusoidal realista (não aleatória)
- ✅ `SensorServerService.kt` — ForegroundService + Ktor + rotas `/sensors`, `/api/sensors`, `/health`
- ✅ `BootReceiver.kt` — auto-start no arranque do tablet
- ✅ `MainActivity.kt` — UI de setup (só usada na primeira configuração)
- ✅ `AndroidManifest.xml` — permissões: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`
- ✅ `res/xml/network_security_config.xml` — cleartext para `localhost` e `127.0.0.1`
- ✅ `res/layout/activity_main.xml`, `strings.xml`, `themes.xml`

---

## Interação 4 — Descoberta: Web Browser control removido do Power Apps

**Data:** 25 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6)

### Contexto
O utilizador tentou inserir o controlo **Web Browser** na Canvas App mas não o encontrou no painel de inserção.

### Prompt
> *"Não encontro o web browser como opção"* (com screenshot do painel Multimédia do Power Apps)

### Análise da AI
Após análise do screenshot, a AI confirmou:
> O controlo **Web Browser foi descontinuado** e removido nas versões recentes do Power Apps. Não está disponível em nenhuma categoria do painel de inserção.

### Alternativas apresentadas pela AI

| Alternativa | Descrição | Viabilidade |
|---|---|---|
| **PCF Component** (TypeScript + iframe) | Criar componente personalizado que embebe iframe → localhost:8080 | ✅ Funciona, requer pac CLI |
| **Android WebView App** | App A passa a ser Kotlin + WebView em vez de Power Apps | ✅ Mais simples, mas abandona Power Apps |

### Decisão
> *"Tem de ser Power Apps a aplicação principal"*

✅ **PCF (Power Apps Component Framework) escolhido** — única via para embutir conteúdo de localhost numa Canvas App sem o controlo Web Browser.

### Aprendizagem registada
> *A remoção de controlos entre versões do Power Apps é um risco real em POCs. A AI identificou o problema a partir do screenshot e apresentou alternativas sem necessidade de pesquisa manual pelo utilizador. O PCF é a solução correcta — e revela que o Power Apps permite extensibilidade profunda via componentes TypeScript.*

---

## Interação 5 — Instalação de ferramentas e criação do PCF component

**Data:** 25 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6) — modo Agent

### Prompt
> *"Valida tudo sff"* → *"Sim"* (confirmação para instalar pac CLI)

### Validação do ambiente executada pela AI

| Ferramenta | Estado encontrado | Acção tomada |
|---|---|---|
| Node.js | ✅ v24.11.1 | Nenhuma |
| npm | ✅ v11.6.2 | Nenhuma |
| pac CLI | ❌ Não instalado | Instalado via `winget install Microsoft.PowerAppsCLI` |
| .NET SDK | ❌ Não instalado | Necessário para `dotnet build` — alternativa encontrada |

### Instalação pac CLI
```
winget install Microsoft.PowerAppsCLI
→ Instalado: Microsoft PowerPlatform CLI v2.4.1
```

### Criação do projecto PCF — SensorViewer

```
pac pcf init --namespace POCBridge --name SensorViewer --template field --run-npm-install
→ Projecto criado com 534 packages instalados
```

### Implementação — `index.ts` (componente PCF)
A AI escreveu o componente TypeScript completo:
- `init()` — cria um `<iframe>` a apontar para `http://localhost:8080/sensors`, com `sandbox="allow-same-origin allow-scripts"` para acesso local
- `updateView()` — actualiza o URL se a propriedade `sensorUrl` for alterada pelo criador da Canvas App
- `startAutoRefresh()` — recarrega o iframe a cada 10 segundos via `setInterval` com timestamp query param (evita cache do browser sem piscar)
- `destroy()` — limpa o timer

**Propriedade configurável exposta ao criador da Canvas App:**
```xml
<property name="sensorUrl" of-type="SingleLine.URL" usage="input" required="false" />
```
Valor por defeito: `http://localhost:8080/sensors` — não requer configuração manual para o caso standard.

### Build do PCF
```
npm run build
→ [build] Succeeded — bundle.js 5.98 KiB gerado sem erros
```

---

## Interação 6 — Empacotamento da solução Power Apps

**Data:** 25 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6) — modo Agent

### Contexto
Para importar o PCF component numa Canvas App, é necessário empacotá-lo numa **solução Power Apps** (`.zip`).

### Abordagem adoptada
O .NET SDK (necessário para `dotnet build` da solução) não estava instalado e o utilizador cancelou a instalação de 212MB. A AI encontrou uma alternativa:

1. **`pac solution init`** → cria estrutura da solução
2. **Construção manual** da estrutura de pastas correcta (`Other/`, `Controls/`)
3. **`pac solution pack`** → empacota em `.zip` sem necessidade de .NET SDK

### Estrutura final da solução Power Apps
```
SensorSolution_v2.zip
  ├── [Content_Types].xml
  ├── customizations.xml          ← regista o custom control pocb_POCBridge.SensorViewer
  ├── solution.xml                ← metadados: publisher POCBridge, prefix pocb
  └── Controls/
        └── pocb_POCBridge.SensorViewer/
              ├── bundle.js       ← 6.5 KB — lógica TypeScript compilada do SensorViewer
              └── ControlManifest.xml
```

### Ficheiros implementados — App A (PCF)
- ✅ `BridgeAppA_PCF/SensorViewerPCF/SensorViewer/index.ts`
- ✅ `BridgeAppA_PCF/SensorViewerPCF/SensorViewer/ControlManifest.Input.xml`
- ✅ `BridgeAppA_PCF/SensorSolution_v2.zip` — **pronto para importar no Power Apps**

### Como usar o SensorSolution_v2.zip no Power Apps
1. [make.powerapps.com](https://make.powerapps.com) → **Solutions** → **Import solution**
2. Seleccionar `SensorSolution_v2.zip`
3. Criar Canvas App → **Insert** → pesquisar `SensorViewer` → componente aparece
4. Inserir e redimensionar (ocupa 100% do espaço atribuído)
5. Os dados aparecem automaticamente quando App B está a correr

> **Licença necessária:** Power Apps Developer Plan (gratuito para devs) ou Premium. Activar em [aka.ms/PowerAppsDevPlan](https://aka.ms/PowerAppsDevPlan)

---

## Arquitectura final da POC

```
[Tablet Android]
  │
  ├── Power Apps Mobile (App A)
  │     └── Canvas App
  │           └── SensorViewer PCF Component
  │                 └── <iframe> → http://localhost:8080/sensors
  │                                         │
  │                              [HTTP local, no dispositivo]
  │                                         │
  └── BridgeAppB.apk (App B) — invisível
        └── SensorServerService (ForegroundService)
              ├── SensorDataGenerator (coroutine, dados a cada 5s)
              └── Ktor HTTP Server @ localhost:8080
                    ├── GET /sensors      → HTML+CSS dashboard
                    ├── GET /api/sensors  → JSON
                    └── GET /health       → "OK"
```

**Fluxo de arranque (após setup inicial):**
1. Tablet liga → `BootReceiver` → `SensorServerService` inicia automaticamente
2. Utilizador abre Power Apps Mobile → abre `SensorDashboard`
3. PCF renderiza iframe → `http://localhost:8080/sensors` → dados visíveis
4. Dados actualizam a cada 10 segundos — sem acção do utilizador
5. **Modo avião activado** → tudo continua a funcionar (Ktor é 100% local)

---

## Reflexão sobre o processo AI-assisted

### O que a AI fez nesta POC

| # | Acção | Valor gerado |
|---|---|---|
| 1 | Elicitação de requisitos via questionário | Evitou pressupostos errados |
| 2 | Identificou constraint crítico do Power Apps (connectors via Azure) | Evitou horas de tentativa-erro |
| 3 | Apresentou 3 alternativas com análise comparativa | Decisão informada em 5 minutos |
| 4 | Detectou remoção do Web Browser control a partir de um screenshot | Adaptação imediata sem pesquisa manual |
| 5 | Encontrou alternativa ao .NET SDK (pac solution pack directo) | Desbloqueou packaging sem instalar 212MB |
| 6 | Escreveu todo o código Kotlin, TypeScript, XML, Gradle | Implementação completa |
| 7 | Documentou decisões e aprendizagens em tempo real | Este documento |

### Constrangimentos descobertos durante o processo (lições aprendidas)

1. **Power Apps connectors rodam na cloud** — impossível aceder a localhost via connector nativo
2. **Web Browser control foi removido** do Power Apps nas versões recentes — documentação pode estar desactualizada
3. **PCF requer licença Premium/Developer** — importante verificar antes de adoptar em produção
4. **.NET SDK é necessário para `dotnet build`** da solução, mas `pac solution pack` funciona sem ele
5. **`foregroundServiceType` é obrigatório no Android 14+** — ausência causa crash em runtime

---

---

## Interação 7 — Build do APK via GitHub Actions

**Data:** 25-26 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6) — modo Agent

### Contexto
O utilizador não tem Android Studio instalado (sem permissões de administrador) e o telemóvel de empresa não permite instalação de APKs fora da Play Store. Foi necessário encontrar uma forma alternativa de compilar o APK.

### Solução adoptada — GitHub Actions
A AI propôs e implementou um pipeline CI/CD no GitHub Actions para compilar o APK na cloud, sem necessidade de ferramentas locais.

### Ficheiros criados pela AI
```
.github/workflows/build-apk.yml   ← Workflow GitHub Actions
BridgeAppB/gradle.properties       ← android.useAndroidX=true
BridgeAppB/gradle/wrapper/gradle-wrapper.properties
BridgeAppB/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
BridgeAppB/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
BridgeAppB/app/src/main/res/drawable/ic_launcher_background.xml
BridgeAppB/app/src/main/res/drawable/ic_launcher_foreground.xml
```

### Pipeline GitHub Actions
```yaml
ubuntu-latest → Java 17 (temurin) → Gradle 8.7 → assembleDebug → upload artifact
```

### Erros encontrados e resolvidos pela AI

| Erro | Causa | Resolução |
|---|---|---|
| `android.useAndroidX` not enabled | Faltava `gradle.properties` | Criado com `android.useAndroidX=true` |
| `mipmap/ic_launcher not found` | Ícones de launcher em falta | Criados adaptive icons em XML (mipmap-anydpi-v26) |

### Resultado
✅ APK compilado com sucesso via GitHub Actions  
✅ Artefacto `BridgeAppB-debug-apk` disponível para download

---

## Interação 8 — Teste end-to-end e validação final

**Data:** 26 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6)

### Contexto
Com o APK compilado, foi necessário instalar e testar no dispositivo Android. Surgiram constrangimentos de segurança corporativa.

### Constrangimentos encontrados

| Constrangimento | Detalhe |
|---|---|
| Power Apps Mobile → erro 4oggx | Conditional Access da NTT bloqueia login em dispositivos não geridos pelo Intune |
| Power Apps Developer Plan → conta pessoal Hotmail | O Developer Plan exige conta de trabalho/escola |
| Microsoft 365 Developer Program | Conta Hotmail não qualifica para sandbox subscription |

### Solução encontrada
**Power Apps via browser Chrome no Android** — o browser não aciona o Conditional Access da mesma forma que a app nativa.

```
Chrome (Android) → make.powerapps.com → Canvas App (Play)
                                               │
                              PCF iframe → localhost:8080
                                               │
                                    BridgeAppB APK (ForegroundService)
```

### Resultado do teste

| Critério | Resultado |
|---|---|
| App B inicia servidor | ✅ Notificação aparece, servidor ativo em localhost:8080 |
| App A carrega dados | ✅ Dados de temperatura, humidade e pressão visíveis |
| Auto-refresh dos dados | ✅ Actualização constante a cada 10 segundos |
| **Modo avião activado** | ✅ **Dados continuam a aparecer e a actualizar** |
| Utilizador não vê App B | ✅ App B completamente invisível ao utilizador |

### **POC VALIDADA COM SUCESSO** ✅

---

## Arquitectura final validada

```
[Dispositivo Android]
  │
  ├── Chrome (App A — simulação)
  │     └── make.powerapps.com → Canvas App
  │           └── SensorViewer PCF Component
  │                 └── <iframe> → http://localhost:8080/sensors
  │                                         │
  │                              [HTTP local, no dispositivo]
  │                                         │
  └── BridgeAppB.apk (App B) — invisível para o utilizador
        └── SensorServerService (ForegroundService)
              ├── SensorDataGenerator (coroutine, dados a cada 5s)
              └── Ktor HTTP Server @ localhost:8080
                    ├── GET /sensors      → HTML+CSS dashboard
                    ├── GET /api/sensors  → JSON
                    └── GET /health       → "OK"
```

---

## Reflexão sobre o processo AI-assisted (actualização final)

### Constrangimentos adicionais descobertos (Interações 7-8)

6. **GitHub Actions como alternativa ao build local** — sem Android Studio, o CI/CD na cloud é uma solução viável e até mais reproduzível
7. **`gradle.properties` é obrigatório** para projetos com dependências AndroidX — não é gerado automaticamente pelo AGP 8.x
8. **Ícones de launcher são obrigatórios** no Android mesmo para debug builds — adaptive icons em XML funcionam sem recursos PNG
9. **Conditional Access corporativo bloqueia apps nativas** mas não necessariamente browsers — importante documentar para contextos enterprise
10. **Browser mobile como alternativa à app nativa** Power Apps — funciona para demos e POCs sem necessidade de dispositivo gerido

---

*Documento actualizado a cada interação AI significativa — última actualização: 26 de Março de 2026 (Interação 8 — POC concluída e validada).*
