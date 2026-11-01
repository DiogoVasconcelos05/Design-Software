# Registo de Utilização de IA — Revisão Crítica da Especificação

Ao contrário da geração das componentes e das fitness functions, o uso da IA para crítica e discussão de ideias é demasiado vasto e talvez ilegível para fazer uma lista completa, então decidimos listar o âmbito das interações e registar as sugestões, em vez de registar as prompts utilizadas uma a uma.

* **Data / Período:** Outubro de 2026
* **Ferramenta / Modelo:** Gemini flash 3.6 / Claude 3.5 Sonnet
* **Ficheiros Submetidos para Revisão:** `01-caracteristicas.md`, `02-componentes.md`, `ADR-001` a `ADR-003`

---

## 1. Âmbito da Interação
A IA foi utilizada como revisora crítica para:
* Testar a coerência dos cenários das Top 3 características condutoras.
* Validar a decomposição da característica composta (*Supportability*).
* Criticar e sugerir melhorias na análise de coesão, acoplamento e instabilidade dos 10 componentes lógicos.
* Avaliar o trade-off do particionamento por domínio versus técnico nas ADRs.

---

## 2. Exemplo de Prompt de Contexto Utilizada
> "Achas que o monolito modular particionado por dominio se adequa às caracteristicas que assinalá-mos como mais importante?"

---

## 3. Principais Sugestões Aceites vs. Rejeitadas
* **Aceites:**
  * Divisão do antigo módulo `billing_payment` em `tariff_pricing` e `payment_invoicing` para acomodar tarifários de câmaras vizinhas sem afetar a lógica fiscal.
  * Formatação dos cenários das Top 3 características segundo o modelo formal (Estímulo, Resposta, Medida de Resposta).
* **Rejeitadas:**