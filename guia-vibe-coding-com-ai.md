# Guia de Vibe Coding com AI — Como Equipas Podem Usar AI para Criar POCs e Projetos

> Um caminho guiado para equipas que querem usar ferramentas de AI (como GitHub Copilot, Claude, ChatGPT) como co-piloto de engenharia, desde a primeira ideia até à validação final.

---

## O que é Vibe Coding com AI?

**Vibe Coding** é uma abordagem de desenvolvimento onde o engenheiro e a AI colaboram em tempo real — o humano define o "o quê" e o "porquê", a AI trata do "como". A AI não substitui o engenheiro: complementa-o, acelerando decisões, escrevendo código boilerplate, identificando constrangimentos e documentando o processo.

A diferença em relação ao uso tradicional de AI:

| Uso tradicional | Vibe Coding |
|---|---|
| "Gera-me código X" | "Ajuda-me a decidir a melhor abordagem para X" |
| Resultado pontual | Processo contínuo e iterativo |
| AI como motor de busca | AI como membro da equipa |
| O engenheiro valida no final | A AI questiona e valida em cada passo |

---

## Quando usar esta abordagem

Esta abordagem é especialmente eficaz em:

- **POCs e protótipos** — onde a velocidade de validação de conceito é crítica
- **Projetos com tecnologias desconhecidas** — a AI compensa a curva de aprendizagem
- **Decisões de arquitectura** — quando há múltiplas opções e é difícil comparar sem experiência prévia
- **Ambientes com constrangimentos** — sem admin, sem licenças, sem hardware específico

---

## O Processo — Fases do Vibe Coding

### Fase 0 — Preparação: define o contexto antes de começar

Antes de escrever qualquer prompt, a equipa deve ter claro:

- **O problema** — o que queremos resolver (não como)
- **As restrições conhecidas** — tecnologias obrigatórias, hardware disponível, permissões de instalação, licenças existentes
- **O critério de sucesso** — o que tem de funcionar para a POC ser considerada válida

> **Dica:** Escreve uma frase de contexto antes do primeiro prompt. Exemplo: *"Quero criar uma POC que demonstre X, usando Y como tecnologia obrigatória, que funcione em Z condições."*

---

### Fase 1 — Elicitação de requisitos com AI

**Objetivo:** A AI ajuda a identificar requisitos que não sabias que eram importantes.

Começa com um prompt aberto e deixa a AI fazer perguntas:

```
"Quero criar [descrição do projeto]. Antes de começarmos,
que perguntas precisas de me fazer para perceber bem o que preciso?"
```

A AI vai questionar aspectos que o humano frequentemente esquece:
- Modelo de conectividade (online, offline, híbrido?)
- Dispositivos alvo e versões mínimas
- Integrações existentes e restrições de segurança
- Quem são os utilizadores e o que não podem notar/fazer

**O que documentar:** as perguntas feitas e as respostas dadas — estas definem os constrangimentos reais do projeto.

---

### Fase 2 — Exploração de alternativas

**Objetivo:** Não aceitar a primeira solução — pedir sempre um leque de opções.

Prompt recomendado:
```
"Dá-me 3 abordagens diferentes para resolver este problema,
com prós e contras de cada uma."
```

Para cada alternativa, pede à AI que avalie explicitamente:
- Complexidade de implementação
- Dependências externas (cloud, licenças, hardware)
- Riscos técnicos específicos ao contexto

**Regra de ouro:** A AI tende a propor a solução mais comum, não necessariamente a melhor para o contexto. Questiona sempre.

---

### Fase 3 — Validação de constrangimentos

**Objetivo:** Identificar bloqueadores técnicos *antes* de implementar.

Depois de escolher uma abordagem, pergunta explicitamente:
```
"Que constrangimentos ou limitações técnicas posso encontrar
ao implementar esta abordagem no meu contexto específico?"
```

Exemplos de constrangimentos que a AI pode identificar antecipadamente:
- APIs ou controlos descontinuados na versão que usas
- Permissões do sistema operativo que bloqueiam certos padrões
- Políticas corporativas (MDM, Conditional Access, firewall) que afetam a solução
- Dependências de licença que o projeto não prevê

**O que fazer quando a AI identifica um bloqueador:**
1. Confirma se o bloqueador é real no teu contexto
2. Pede alternativas imediatamente — não voltes à fase 2 manualmente
3. Documenta o bloqueador e a solução encontrada

---

### Fase 4 — Implementação guiada

**Objetivo:** A AI escreve código, tu defines o rumo.

Boas práticas nesta fase:

- **Implementa em incrementos pequenos** — pede um ficheiro ou módulo de cada vez, valida, depois avança
- **Dá contexto acumulado** — a AI não sabe o que foi decidido nas fases anteriores a menos que o digas. Inclui sempre: *"Tendo em conta que decidimos usar X e que o constrangimento Y se aplica..."*
- **Valida antes de avançar** — não peças os próximos 5 ficheiros sem garantir que o anterior funciona
- **Quando algo falha**, copia a mensagem de erro completa para a AI — não parafrasees

Estrutura de prompt para implementação:
```
"Com base na arquitectura que definimos [descreve brevemente],
implementa [componente específico]. Considera [constrangimento relevante]."
```

---

### Fase 5 — Debugging colaborativo

**Objetivo:** Usar a AI para diagnosticar erros sem perder tempo em tentativa-erro.

Quando surge um erro, partilha **sempre**:
1. O erro completo (stack trace incluído, se existir)
2. O que estavas a tentar fazer quando o erro ocorreu
3. O que já tentaste para o resolver

A AI vai identificar a causa raiz e propor a correção. Se a primeira correção não resolver, **não repitas o mesmo prompt** — adiciona o novo erro à conversa.

> **Anti-padrão a evitar:** Pedir à AI para "tentar outra coisa" sem dar o output do erro. A AI vai adivinhar — e adivinhar mal.

---

### Fase 6 — Documentação em tempo real

**Objetivo:** Documentar decisões enquanto são tomadas, não no fim.

A documentação do processo tem mais valor do que a documentação do código:
- **O que foi decidido** e porque foi decidido assim
- **O que foi rejeitado** e porque foi rejeitado
- **Os constrangimentos descobertos** e como foram resolvidos

Prompt para documentar uma decisão:
```
"Documentar esta decisão no nosso ficheiro de processo:
decidimos [X] em vez de [Y] porque [Z]."
```

A AI pode manter um ficheiro `.md` atualizado à medida que o projeto avança — é o registo do raciocínio da equipa, não apenas do resultado.

---

### Fase 7 — Teste e validação

**Objetivo:** Validar o critério de sucesso definido na Fase 0.

Antes de declarar a POC concluída, testa explicitamente cada critério de sucesso. Para cada um que falhe:

1. Descreve o comportamento esperado vs. o comportamento observado
2. Dá esse contexto à AI
3. Resolve o problema antes de avançar para o próximo critério

Quando tudo passa, pede à AI um resumo do estado final para fechar o ficheiro de processo.

---

## Boas Práticas Transversais

### O que fazer
- **Questiona as sugestões da AI** — especialmente quando algo parece demasiado simples ou demasiado complexo
- **Dá feedback explícito** — "isto não se aplica ao meu contexto porque..." leva a respostas muito mais úteis
- **Mantém uma conversa contínua** — o contexto acumulado melhora drasticamente a qualidade das respostas
- **Usa a AI para pesquisa rápida** — "que alternativas existem para X?" é mais eficiente do que pesquisar documentação

### O que evitar
- **Aceitar código sem ler** — a AI pode gerar código que funciona mas não é seguro ou não segue as boas práticas da equipa
- **Pedir tudo de uma vez** — projetos grandes divididos em pedaços grandes geram erros difíceis de rastrear
- **Ignorar os avisos da AI** — quando a AI diz "atenção, isto pode ter um problema em...", leva a sério
- **Depender de uma única ferramenta** — diferentes ferramentas AI têm pontos fortes diferentes; combina-as quando faz sentido

---

## Anti-padrões Comuns

| Anti-padrão | Consequência | Alternativa |
|---|---|---|
| "Faz tudo de uma vez" | Erros difíceis de localizar, contexto perdido | Implementa em incrementos validados |
| "Já chegou, avança" | Bugs escondidos que aparecem no teste final | Valida cada componente antes de avançar |
| "A AI disse, deve estar certo" | Código que não serve o contexto real | Questiona sempre a aplicabilidade ao teu caso |
| Não documentar as decisões rejeitadas | A equipa repete os mesmos erros em projetos futuros | Documenta o que foi rejeitado e porquê |
| Parafrasear erros para a AI | A AI não consegue diagnosticar corretamente | Copia sempre o erro completo |

---

## Como Estruturar o Ficheiro de Processo

Recomenda-se criar um ficheiro `ai-process.md` desde o primeiro dia com esta estrutura:

```markdown
# [Nome do Projeto] — Processo AI-assisted

## Contexto e Objetivo

## Interação N — [Título descritivo]
**Data:** ...
**Prompt:** ...
**Análise da AI:** ...
**Decisão tomada:** ...
**Constrangimentos descobertos:** ...
**Aprendizagem:** ...
```

Este ficheiro serve dois propósitos:
1. **Durante o projeto** — guia a conversa com a AI e evita repetir contexto
2. **Após o projeto** — registo de conhecimento para equipas futuras e demonstração do valor da AI

---

## Exemplo Prático — POC BridgeAPP (NTT Data, Março 2026)

Este guia foi criado a partir de uma POC real. Aqui fica o resumo de como cada fase foi aplicada.

### Contexto
Criar uma POC com duas aplicações — uma em Power Apps (App A) e uma Android em Kotlin (App B) — que comunicassem entre si em modo 100% offline, com o utilizador a não perceber que havia duas apps envolvidas.

### Como as fases foram aplicadas

**Fase 1 — Elicitação de requisitos**  
A AI fez um questionário estruturado: tipo de dados, modelo de rede, grau de offline, tipo de Power App. Uma das respostas críticas ("100% offline, sem internet de todo") eliminou imediatamente as soluções cloud-first que seriam propostas de seguida.

**Fase 2 — Exploração de alternativas**  
Foram apresentadas 3 opções: Deep Link Round-Trip, Local HTTP Server e híbrido. A AI avaliou cada uma contra o critério "utilizador não deve reparar na troca de app". O Deep Link foi eliminado pelo flash visual de ~500ms. O Local HTTP Server foi escolhido.

**Fase 3 — Validação de constrangimentos**  
Ao tentar inserir o controlo Web Browser no Power Apps, o utilizador descobriu que tinha sido removido nas versões recentes. A AI identificou o problema a partir de um screenshot e propôs o PCF (Power Apps Component Framework) como alternativa — um componente TypeScript que embute um iframe.

**Fase 4 — Implementação**  
A AI implementou todos os ficheiros Kotlin (ForegroundService, Ktor HTTP server, BroadcastReceiver), o componente PCF em TypeScript e todos os ficheiros de configuração Gradle/Android. O desenvolvimento foi incremental: App B primeiro, App A depois.

**Fase 5 — Debugging**  
Dois erros no GitHub Actions foram resolvidos com stack traces completos: `android.useAndroidX` não estava ativo (resolvido com `gradle.properties`) e ícones de launcher em falta (resolvidos com adaptive icons em XML).

**Fase 6 — Documentação**  
O ficheiro `poc-ai-process.md` foi mantido atualizado em cada interação, registando decisões, alternativas rejeitadas e constrangimentos descobertos.

**Fase 7 — Teste**  
O Conditional Access corporativo bloqueou o login na app nativa Power Apps. A AI identificou que o browser Chrome no Android não aciona a mesma política — o teste foi feito via browser. Com modo avião ativo, os dados continuaram a atualizar. POC validada.

### Repositório de referência
[github.com/angelo-cocharra-ntt/BridgeAPP](https://github.com/angelo-cocharra-ntt/BridgeAPP)

---

*Documento criado com AI (GitHub Copilot — Claude Sonnet 4.6) como parte do processo que descreve. Março de 2026.*
