# POC BridgeAPP — Variante Deep Link — Processo de Criação com AI

> Este documento regista as interações com AI que guiaram a criação da variante Deep Link desta POC.
> Deve ser lido em conjunto com o `poc-ai-process.md`, que documenta a variante original (HTTP Server).
> O objectivo é demonstrar como a AI adapta uma solução existente a uma nova abordagem arquitectural.

---

## Contexto

**Data de início:** 27 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6)  
**Base:** Variante HTTP Server já validada (ver `poc-ai-process.md`)

**Motivo da nova variante:**  
Após a validação da POC original (HTTP Server + ForegroundService), a equipa recebeu feedback técnico de que a abordagem com ForegroundService podia ter impedimentos em contexto de produção com o Dynamics 365 Field Service, nomeadamente:

- Categorias de foreground service implicam diferentes permissões consoante a versão Android
- A app principal (Dynamics 365 FS) precisaria de um conjunto de permissões e validações para invocar uma app externa
- Consoante o tipo de foreground service, poderia ser necessário gerar as duas apps com o mesmo keychain
- Qualquer ForegroundService exige sempre uma app base com consentimento explícito do utilizador

**Objectivo desta variante:** Criar uma implementação alternativa, usando **Deep Link** como mecanismo de comunicação, para avaliar os tradeoffs face à abordagem HTTP Server.

---

## Interação 1 — Análise dos impedimentos e decisão de nova variante

**Data:** 27 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6)

### Prompt
> *"Apontaram-me estes problemas ao usar a APP B como foreground service, faz sentido? [...] Podemos fazer esta mesma POC mas como deeplink? Não quero estragar a atual, faz tudo em separado pff"*

### Análise da AI aos impedimentos levantados

A AI avaliou cada ponto ponto a ponto:

| Impedimento levantado | Válido? | Análise |
|---|---|---|
| Categorias de FS com permissões diferentes | ✅ Válido e já tratado | `foregroundServiceType="dataSync"` já declarado; em produção pode implicar revisão Google Play |
| Dynamics precisar invocar a App B | ⚠️ Parcialmente válido | Na abordagem HTTP Server, o Dynamics **não invoca** a App B — faz pedidos HTTP a `localhost:8080`. Seria válido se usássemos Deep Links ou AIDL |
| Mesmo keychain entre apps | ❌ Não se aplica | Keychain partilhada é necessária para IPC Android (AIDL, Content Providers com `signature` permissions). No HTTP local, qualquer app pode aceder a `localhost:8080` sem relação de assinatura |
| App base com consentimento do utilizador | ✅ Válido e já implementado | `MainActivity` com botão "Iniciar Servidor" já gere isto |

**Risco real identificado pela AI (não mencionado nos impedimentos):**
> As políticas **Intune MAM** do Dynamics 365 Field Service podem restringir o que o WebView carrega — incluindo `localhost`. Este seria o maior bloqueador numa implementação real e deveria ser testado antes de avançar para produção.

### Decisão
✅ **Criar variante Deep Link em separado**, mantendo a variante HTTP Server intacta como POC original.

---

## Interação 2 — Arquitectura da variante Deep Link

**Data:** 27 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6) — modo Agent

### Arquitectura definida pela AI

```
[Canvas App — Power Apps]
  └── Botão "Atualizar Sensores"
        └── Launch("bridgeappb://getsensors?returnUrl=[URL da Canvas App]")
                    │
                    ▼ Chrome abre App B (flash ~300ms)
                    │
        [BridgeAppB_DeepLink APK]
          └── MainActivity recebe o Intent do deep link
          └── SensorDataGenerator gera leitura instantânea
          └── startActivity → Chrome abre returnUrl?temperature=X&humidity=Y&pressure=Z
          └── finish() — App B fecha-se imediatamente
                    │
                    ▼ Chrome regressa à Canvas App com dados no URL
                    │
        [Canvas App]
          └── Param("temperature") → label de temperatura
          └── Param("humidity")    → label de humidade
          └── Param("pressure")    → label de pressão
```

### Decisões de implementação

| Decisão | Escolha | Justificação |
|---|---|---|
| `launchMode` da Activity | `singleTask` | Evita acumulação de instâncias ao receber múltiplos deep links |
| `noHistory="true"` no Manifest | Sim | App B não aparece no histórico de apps recentes |
| Geração de dados | Síncrona (sem coroutine) | A leitura é instantânea — não há servidor a manter |
| Sem ForegroundService | Sim | App B abre e fecha — sem necessidade de background |
| Sem permissões especiais | Sim | Não precisa de `INTERNET`, `FOREGROUND_SERVICE` nem `POST_NOTIFICATIONS` |
| `returnUrl` codificado com `EncodeUrl()` | Sim (Power Apps) | Evita que `&` e `?` do URL da Canvas App partam o query string do deep link |

### Tradeoffs documentados pela AI

| Critério | HTTP Server (variante original) | Deep Link (esta variante) |
|---|---|---|
| Flash visível de App B | ❌ Nenhum | ⚠️ ~300ms ao atualizar |
| Auto-refresh contínuo | ✅ A cada 10s automático | ❌ Manual (botão) |
| App B precisa de estar em background | ✅ ForegroundService | ❌ Não precisa |
| Recarrega Power Apps a cada leitura | ❌ Não recarrega | ✅ Recarrega sempre |
| Permissões Android necessárias | INTERNET, FOREGROUND_SERVICE | Nenhuma |
| Compatibilidade com políticas MAM/Intune | ⚠️ A validar (localhost no WebView) | ✅ Sem WebView envolvido |
| Dados em tempo real | ✅ Streaming contínuo | ❌ Snapshot por pedido |

---

## Interação 3 — Implementação

**Data:** 27 de Março de 2026  
**Ferramenta AI:** GitHub Copilot (Claude Sonnet 4.6) — modo Agent

### Ficheiros implementados — BridgeAppB_DeepLink

| Ficheiro | Descrição |
|---|---|
| `settings.gradle.kts` | Configuração do projecto Gradle |
| `build.gradle.kts` | Plugins root-level |
| `gradle.properties` | `android.useAndroidX=true` (lição aprendida da variante anterior) |
| `gradle/libs.versions.toml` | Versões: AGP 8.5.0, Kotlin 1.9.24, AndroidX (sem Ktor — não é necessário) |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.7 |
| `app/build.gradle.kts` | Configuração da app: `minSDK=26`, `targetSDK=35`, Java 17 |
| `AndroidManifest.xml` | `intent-filter` para `bridgeappb://getsensors`; `launchMode=singleTask`; `noHistory=true` |
| `MainActivity.kt` | Lê `returnUrl` do Intent, chama `SensorDataGenerator`, redireciona e termina |
| `SensorDataGenerator.kt` | Geração síncrona com variação sinusoidal (reutiliza lógica da variante HTTP Server) |
| `SensorReading.kt` | Data class simples (sem helpers de HTML — não são necessários) |
| Ícones e recursos | Adaptive icons (lição aprendida), layout informativo, temas Material |

### Aprendizagens aplicadas da variante anterior (sem erros repetidos)

| Problema anterior | Solução aplicada preventivamente |
|---|---|
| `gradle.properties` em falta → erro AndroidX | Incluído desde o início com `android.useAndroidX=true` |
| Ícones de launcher em falta → erro AAPT | Adaptive icons em XML criados desde o início |

### Ficheiros de suporte

- **`.github/workflows/build-apk-deeplink.yml`** — Workflow separado, acionado apenas por alterações em `BridgeAppB_DeepLink/`
- **`DEEPLINK_SETUP.md`** — Instruções passo a passo para configurar a Canvas App: fórmulas `Launch()`, `Param()`, `EncodeUrl()` e label de timestamp

---

## Arquitectura final da variante Deep Link

```
[Dispositivo Android]
  │
  ├── Chrome (App A — Canvas App Power Apps)
  │     └── Botão → Launch("bridgeappb://getsensors?returnUrl=[URL]")
  │     └── Param("temperature") / Param("humidity") / Param("pressure") nos labels
  │
  └── BridgeAppB_DeepLink.apk (App B)
        └── MainActivity (singleTask, noHistory)
              ├── Recebe deep link → extrai returnUrl
              ├── SensorDataGenerator.generate() → leitura síncrona instantânea
              ├── Redireciona para returnUrl?temperature=X&humidity=Y&pressure=Z
              └── finish() — fecha-se imediatamente
```

**Fluxo por leitura:**
1. Utilizador prime **"🔄 Atualizar Sensores"** na Canvas App
2. Chrome abre `bridgeappb://getsensors?returnUrl=[...]` → Android abre App B
3. App B gera dados, abre Chrome com dados no URL, termina
4. Canvas App recarrega com `Param("temperature")` etc. já preenchidos
5. Labels mostram os novos valores → utilisador vê resultado em ~1-2 segundos

---

## Reflexão comparativa — HTTP Server vs. Deep Link

### Quando usar cada abordagem

**HTTP Server (variante original):**
- Quando a experiência do utilizador tem de ser completamente fluida (sem flash, sem interrupção)
- Quando os dados precisam de atualizar automaticamente (monitorização contínua)
- Quando o contexto de produção não impõe restrições ao WebView (sem Intune MAM restritivo)

**Deep Link (esta variante):**
- Quando as políticas MDM/MAM da organização bloqueiam ForegroundServices ou WebViews a localhost
- Quando a app principal é o Dynamics 365 Field Service ou outra app gerida pelo Intune
- Quando a leitura de dados é pontual (check-in, registo de ocorrência) e não contínua
- Quando se quer evitar permissões Android adicionais

### Constrangimentos específicos da variante Deep Link

1. **O utilizador vê brevemente o App B** (~300ms de flash) — inevitável no modelo Deep Link
2. **Dados não atualizam sozinhos** — o utilizador tem de carregar no botão para cada leitura
3. **Canvas App recarrega** a cada leitura — perde estado local (variáveis, navegação)
4. **`EncodeUrl()` é obrigatório** no `Launch()` do Power Apps — sem ele, o `&` do URL da Canvas App parte o query string do deep link

---

*Documento criado com AI (GitHub Copilot — Claude Sonnet 4.6). 27 de Março de 2026.*
