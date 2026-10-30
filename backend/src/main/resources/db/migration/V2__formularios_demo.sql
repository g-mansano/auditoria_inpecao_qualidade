-- Formulários de demonstração da empresa fictícia "Acme Indústria".
-- Perguntas, processos, documentos e setores são exemplos genéricos escritos para a demo;
-- as referências citam apenas o número da cláusula/capítulo das normas públicas.

-- ---------------------------------------------------------------- 1. ISO 13485
INSERT INTO template_checklist (codigo, revisao, titulo, tipo, nome, ordem, rotulo_auditoria, rotulo_referencia, usa_processo, usa_documentos, usa_local, opcoes_conformidade, exige_registro_nc, escopo_padrao, sugestoes_evidencia, rodape) VALUES
('FQ-101', '06', 'Checklist de Auditoria Interna ISO 13485', 'AUDITORIA', 'ISO 13485', 1, 'Auditoria Interna', 'Cláusula', TRUE, TRUE, FALSE,
 'C:C:Conforme|NC:NC:Não Conforme|OM:OM:Oportunidade de Melhoria|NA:NA:Não Aplicável', TRUE,
 'ISO 13485:2016. Projeto, fabricação e distribuição de dispositivos médicos (empresa fictícia).', NULL,
 'FQ-101 - Rev. 06 - Data: 02/03/2026');

INSERT INTO pergunta (template_id, numero, secao, grupo, clausula, texto, processo_padrao, documentos_padrao) VALUES
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 1, 'CHECKLIST', '4 · Sistema de gestão da qualidade', '4.1', 'Os processos do SGQ estão definidos, com sequência e interação mapeadas?', 'PG-01 - Gestão da Qualidade', 'MQ-01 - Manual da Qualidade'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 2, 'CHECKLIST', '4 · Sistema de gestão da qualidade', '4.1.5', 'Processos terceirizados são controlados por contrato e critérios de qualidade?', 'PG-01 - Gestão da Qualidade', 'PQ-07 - Qualificação de Fornecedores'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 3, 'CHECKLIST', '4 · Sistema de gestão da qualidade', '4.1.6', 'Há validação documentada dos softwares usados nos processos do SGQ?', 'PG-01 - Gestão da Qualidade', 'PQ-12 - Validação de Software'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 4, 'CHECKLIST', '4 · Sistema de gestão da qualidade', '4.2.3', 'Existe arquivo técnico para cada família de dispositivo?', 'PG-04 - Engenharia', 'Arquivo técnico do produto'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 5, 'CHECKLIST', '4 · Sistema de gestão da qualidade', '4.2.4', 'Documentos são aprovados e revisados antes da distribuição, com versão vigente disponível no uso?', 'PG-01 - Gestão da Qualidade', 'PQ-01 - Controle de Documentos'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 6, 'CHECKLIST', '4 · Sistema de gestão da qualidade', '4.2.5', 'Registros são legíveis, recuperáveis e guardados pelo prazo definido?', 'PG-01 - Gestão da Qualidade', 'PQ-02 - Controle de Registros'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 7, 'CHECKLIST', '5 · Responsabilidade da direção', '5.1', 'A alta direção demonstra comprometimento com o SGQ (recursos e análises críticas)?', 'PG-02 - Gestão do Negócio', 'MQ-01 - Manual da Qualidade'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 8, 'CHECKLIST', '5 · Responsabilidade da direção', '5.3', 'A política da qualidade está comunicada e é entendida pelos colaboradores?', 'PG-02 - Gestão do Negócio', 'MQ-01 - Manual da Qualidade'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 9, 'CHECKLIST', '5 · Responsabilidade da direção', '5.4.1', 'Os objetivos da qualidade são mensuráveis e acompanhados?', 'PG-02 - Gestão do Negócio', 'Painel de indicadores'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 10, 'CHECKLIST', '5 · Responsabilidade da direção', '5.5.1', 'Responsabilidades e autoridades estão definidas e comunicadas?', 'PG-03 - Recursos Humanos', 'Organograma e descrições de cargo'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 11, 'CHECKLIST', '5 · Responsabilidade da direção', '5.6', 'A análise crítica pela direção ocorre na frequência planejada e gera ações?', 'PG-02 - Gestão do Negócio', 'Ata de análise crítica'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 12, 'CHECKLIST', '6 · Gestão de recursos', '6.2', 'As competências estão definidas e os treinamentos registrados e avaliados?', 'PG-03 - Recursos Humanos', 'PQ-05 - Treinamento'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 13, 'CHECKLIST', '6 · Gestão de recursos', '6.3', 'A manutenção preventiva da infraestrutura está planejada e registrada?', 'PG-06 - Manutenção', 'Plano de manutenção'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 14, 'CHECKLIST', '6 · Gestão de recursos', '6.4', 'O ambiente de trabalho e o controle de contaminação são monitorados?', 'PG-05 - Produção', 'Registros de monitoramento ambiental'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 15, 'CHECKLIST', '7 · Realização do produto', '7.1', 'O planejamento da realização do produto considera a gestão de risco?', 'PG-04 - Engenharia', 'Arquivo de gerenciamento de risco'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 16, 'CHECKLIST', '7 · Realização do produto', '7.2', 'Requisitos do cliente e regulatórios são analisados antes de aceitar um pedido?', 'PG-08 - Comercial', 'PQ-09 - Análise de Pedidos'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 17, 'CHECKLIST', '7 · Realização do produto', '7.3', 'As etapas de projeto (entradas, saídas, verificação e validação) estão registradas?', 'PG-04 - Engenharia', 'PQ-10 - Projeto e Desenvolvimento'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 18, 'CHECKLIST', '7 · Realização do produto', '7.4', 'Fornecedores são avaliados e monitorados conforme o risco do item comprado?', 'PG-07 - Suprimentos', 'PQ-07 - Qualificação de Fornecedores'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 19, 'CHECKLIST', '7 · Realização do produto', '7.5.1', 'A produção segue instruções de trabalho e registros de lote?', 'PG-05 - Produção', 'Instruções de trabalho'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 20, 'CHECKLIST', '7 · Realização do produto', '7.5.8', 'Produtos são identificados e rastreáveis em todas as etapas?', 'PG-05 - Produção', 'PQ-11 - Rastreabilidade'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 21, 'CHECKLIST', '7 · Realização do produto', '7.6', 'Equipamentos de medição estão calibrados, identificados e protegidos de ajustes indevidos?', 'PG-06 - Manutenção', 'Plano de calibração'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 22, 'CHECKLIST', '8 · Medição, análise e melhoria', '8.2.1', 'Há coleta de retroalimentação do cliente e de dados pós-mercado?', 'PG-08 - Comercial', 'Pesquisa de satisfação'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 23, 'CHECKLIST', '8 · Medição, análise e melhoria', '8.2.2', 'Reclamações são registradas, investigadas e respondidas no prazo?', 'PG-01 - Gestão da Qualidade', 'PQ-03 - Tratamento de Reclamações'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 24, 'CHECKLIST', '8 · Medição, análise e melhoria', '8.3', 'Produto não conforme é identificado, segregado e tratado com registro?', 'PG-05 - Produção', 'PQ-04 - Produto Não Conforme'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-101'), 25, 'CHECKLIST', '8 · Medição, análise e melhoria', '8.5.2', 'Ações corretivas atacam a causa raiz e têm a eficácia verificada?', 'PG-01 - Gestão da Qualidade', 'PQ-06 - Ações Corretivas');

-- ---------------------------------------------------------------- 2. ANVISA (Boas Práticas de Fabricação)
-- Duas seções: CHECKLIST por etapa e CAMPO (roteiro de inspeção por setor, com responsável e orientação).
INSERT INTO template_checklist (codigo, revisao, titulo, tipo, nome, ordem, rotulo_auditoria, rotulo_referencia, usa_processo, usa_documentos, usa_local, opcoes_conformidade, exige_registro_nc, escopo_padrao, sugestoes_evidencia, rodape) VALUES
('FQ-102', '04', 'Checklist de Auditoria Interna – Boas Práticas de Fabricação', 'AUDITORIA', 'ANVISA', 2, 'Auditoria Interna', 'Capítulo / Seção', FALSE, TRUE, FALSE,
 'C:C:Conforme|NC:NC:Não Conforme|OM:OM:Oportunidade de Melhoria|NA:NA:Não Aplicável', TRUE,
 NULL, 'Documentação|Visual|Entrevista',
 'FQ-102 - Rev. 04 - Data: 10/02/2026');

INSERT INTO pergunta (template_id, numero, secao, grupo, clausula, texto, documentos_padrao, orientacao_padrao, responsavel_padrao) VALUES
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 1, 'CHECKLIST', 'Requisitos gerais', 'Cap. II, seção I', 'A empresa mantém um manual da qualidade atualizado?', 'MQ-01', 'Documentação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 2, 'CHECKLIST', 'Requisitos gerais', 'Cap. II, seção I', 'O sistema da qualidade cobre todas as linhas de produto fabricadas?', 'MQ-01', 'Documentação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 3, 'CHECKLIST', 'Responsabilidade gerencial', 'Cap. II, seção II', 'A política e os objetivos da qualidade estão aprovados pela direção?', 'MQ-01', 'Documentação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 4, 'CHECKLIST', 'Responsabilidade gerencial', 'Cap. II, seção II', 'Há um representante da direção designado formalmente?', 'Portaria interna', 'Documentação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 5, 'CHECKLIST', 'Pessoal', 'Cap. II, seção III', 'Os colaboradores recebem treinamento em boas práticas antes de assumir a função?', 'PQ-05', 'Entrevista', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 6, 'CHECKLIST', 'Pessoal', 'Cap. II, seção III', 'Existem requisitos de saúde e higiene para quem trabalha na produção?', 'PQ-08', 'Visual', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 7, 'CHECKLIST', 'Documentos e registros', 'Cap. III', 'Documentos obsoletos são recolhidos e identificados?', 'PQ-01', 'Documentação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 8, 'CHECKLIST', 'Documentos e registros', 'Cap. III', 'O registro histórico de cada lote está completo e assinado?', 'Registro de lote', 'Documentação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 9, 'CHECKLIST', 'Controle de compras', 'Cap. IV', 'Fornecedores críticos estão qualificados e reavaliados periodicamente?', 'PQ-07', 'Documentação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 10, 'CHECKLIST', 'Controle de compras', 'Cap. IV', 'Materiais recebidos ficam em quarentena até a liberação?', 'PQ-13', 'Visual', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 11, 'CHECKLIST', 'Controle de produção', 'Cap. V', 'As instruções de fabricação estão disponíveis no posto de trabalho?', 'Instruções de trabalho', 'Visual', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 12, 'CHECKLIST', 'Controle de produção', 'Cap. V', 'Os processos especiais estão validados?', 'Relatórios de validação', 'Documentação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 13, 'CHECKLIST', 'Controle de produção', 'Cap. V', 'A limpeza de equipamentos é registrada entre lotes?', 'Registros de limpeza', 'Visual', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 14, 'CHECKLIST', 'Não conformidades', 'Cap. VI', 'Não conformidades são investigadas e têm ação definida?', 'PQ-04', 'Documentação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 15, 'CHECKLIST', 'Não conformidades', 'Cap. VI', 'Reclamações de clientes são avaliadas quanto à necessidade de notificação?', 'PQ-03', 'Documentação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 16, 'CHECKLIST', 'Não conformidades', 'Cap. VI', 'Existe procedimento de recolhimento de produto testado?', 'PQ-14', 'Entrevista', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 17, 'CAMPO', 'Almoxarifado', NULL, 'Materiais estão identificados com status (quarentena, aprovado, reprovado)?', NULL, 'Visual', 'Líder do almoxarifado'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 18, 'CAMPO', 'Almoxarifado', NULL, 'O armazenamento respeita o empilhamento máximo e o afastamento das paredes?', NULL, 'Visual', 'Líder do almoxarifado'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 19, 'CAMPO', 'Almoxarifado', NULL, 'Temperatura e umidade são registradas diariamente?', NULL, 'Documentação', 'Líder do almoxarifado'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 20, 'CAMPO', 'Almoxarifado', NULL, 'O operador sabe explicar o fluxo de recebimento?', NULL, 'Entrevista', 'Líder do almoxarifado'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 21, 'CAMPO', 'Produção', NULL, 'A linha está identificada com o produto e o lote em processo?', NULL, 'Visual', 'Supervisor de produção'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 22, 'CAMPO', 'Produção', NULL, 'Os instrumentos em uso estão com a calibração em dia?', NULL, 'Visual', 'Supervisor de produção'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 23, 'CAMPO', 'Produção', NULL, 'O registro de lote está sendo preenchido no momento da atividade?', NULL, 'Documentação', 'Supervisor de produção'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-102'), 24, 'CAMPO', 'Produção', NULL, 'O operador conhece o procedimento em caso de desvio?', NULL, 'Entrevista', 'Supervisor de produção');

-- ---------------------------------------------------------------- 3. ISO 9001 (escala sem OM)
INSERT INTO template_checklist (codigo, revisao, titulo, tipo, nome, ordem, rotulo_auditoria, rotulo_referencia, usa_processo, usa_documentos, usa_local, opcoes_conformidade, exige_registro_nc, escopo_padrao, sugestoes_evidencia, rodape) VALUES
('FQ-103', '04', 'Checklist de Auditoria Interna ISO 9001:2015', 'AUDITORIA', 'ISO 9001', 3, 'Auditoria Interna', 'Cláusula', TRUE, TRUE, FALSE,
 'C:C:Conforme|NC:NC:Não Conforme|NA:NA:Não Aplicável', TRUE,
 NULL, NULL,
 'FQ-103 - Rev. 04 - Data: 15/01/2026');

INSERT INTO pergunta (template_id, numero, secao, grupo, clausula, texto, processo_padrao, documentos_padrao, orientacao_padrao) VALUES
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 1, 'CHECKLIST', '4 · Contexto da organização', '4.1', 'Questões internas e externas relevantes estão identificadas e revisadas?', 'PG-02 - Gestão do Negócio', 'Análise SWOT', 'Verificar data da última revisão'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 2, 'CHECKLIST', '4 · Contexto da organização', '4.2', 'Partes interessadas e suas expectativas estão mapeadas?', 'PG-02 - Gestão do Negócio', 'Matriz de partes interessadas', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 3, 'CHECKLIST', '4 · Contexto da organização', '4.3', 'O escopo do SGQ está documentado, com justificativa das exclusões?', 'PG-01 - Gestão da Qualidade', 'MQ-01 - Manual da Qualidade', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 4, 'CHECKLIST', '5 · Liderança', '5.1', 'A liderança promove a abordagem de processos e a mentalidade de risco?', 'PG-02 - Gestão do Negócio', NULL, 'Entrevistar a direção'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 5, 'CHECKLIST', '5 · Liderança', '5.2', 'A política da qualidade está disponível às partes interessadas?', 'PG-01 - Gestão da Qualidade', 'Política da Qualidade', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 6, 'CHECKLIST', '6 · Planejamento', '6.1', 'Riscos e oportunidades têm ações planejadas e avaliadas?', 'PG-02 - Gestão do Negócio', 'Matriz de riscos', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 7, 'CHECKLIST', '6 · Planejamento', '6.2', 'Os objetivos da qualidade têm meta, prazo e responsável?', 'PG-02 - Gestão do Negócio', 'Painel de indicadores', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 8, 'CHECKLIST', '6 · Planejamento', '6.3', 'Mudanças no SGQ são planejadas e comunicadas?', 'PG-01 - Gestão da Qualidade', 'Registro de mudanças', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 9, 'CHECKLIST', '7 · Apoio', '7.1.5', 'Recursos de monitoramento e medição são adequados e calibrados?', 'PG-06 - Manutenção', 'Plano de calibração', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 10, 'CHECKLIST', '7 · Apoio', '7.2', 'A eficácia dos treinamentos é avaliada?', 'PG-03 - Recursos Humanos', 'PQ-05 - Treinamento', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 11, 'CHECKLIST', '7 · Apoio', '7.5', 'A informação documentada está protegida contra perda e uso indevido?', 'PG-01 - Gestão da Qualidade', 'PQ-01 - Controle de Documentos', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 12, 'CHECKLIST', '8 · Operação', '8.2', 'Requisitos de produtos e serviços são confirmados com o cliente?', 'PG-08 - Comercial', 'PQ-09 - Análise de Pedidos', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 13, 'CHECKLIST', '8 · Operação', '8.4', 'Provedores externos são avaliados com critérios definidos?', 'PG-07 - Suprimentos', 'PQ-07 - Qualificação de Fornecedores', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 14, 'CHECKLIST', '8 · Operação', '8.5.1', 'A produção ocorre sob condições controladas?', 'PG-05 - Produção', 'Instruções de trabalho', 'Acompanhar uma etapa na linha'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 15, 'CHECKLIST', '8 · Operação', '8.7', 'Saídas não conformes são identificadas e controladas?', 'PG-05 - Produção', 'PQ-04 - Produto Não Conforme', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 16, 'CHECKLIST', '9 · Avaliação de desempenho', '9.1.2', 'A satisfação do cliente é monitorada e analisada?', 'PG-08 - Comercial', 'Pesquisa de satisfação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 17, 'CHECKLIST', '9 · Avaliação de desempenho', '9.2', 'O programa de auditorias internas foi cumprido?', 'PG-01 - Gestão da Qualidade', 'Programa de auditorias', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 18, 'CHECKLIST', '9 · Avaliação de desempenho', '9.3', 'A análise crítica pela direção considera todas as entradas exigidas?', 'PG-02 - Gestão do Negócio', 'Ata de análise crítica', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 19, 'CHECKLIST', '10 · Melhoria', '10.2', 'Não conformidades geram ação corretiva com análise de causa?', 'PG-01 - Gestão da Qualidade', 'PQ-06 - Ações Corretivas', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-103'), 20, 'CHECKLIST', '10 · Melhoria', '10.3', 'Há evidências de melhoria contínua nos indicadores?', 'PG-02 - Gestão do Negócio', 'Painel de indicadores', NULL);

-- ---------------------------------------------------------------- 4. INMETRO (escala S/N/NA, sem colunas extras)
INSERT INTO template_checklist (codigo, revisao, titulo, tipo, nome, ordem, rotulo_auditoria, rotulo_referencia, usa_processo, usa_documentos, usa_local, opcoes_conformidade, exige_registro_nc, escopo_padrao, sugestoes_evidencia, rodape) VALUES
('FQ-104', '01', 'Checklist de Auditoria Interna – Conformidade INMETRO', 'AUDITORIA', 'INMETRO', 4, 'Auditoria Interna', NULL, FALSE, FALSE, FALSE,
 'C:S:Sim|NC:N:Não|NA:NA:Não se aplica', FALSE,
 NULL, NULL,
 'FQ-104 - Rev.: 01 - Data: 20/04/2026');

INSERT INTO pergunta (template_id, numero, secao, grupo, clausula, texto, orientacao_padrao) VALUES
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 1, 'CHECKLIST', 'INSTALAÇÕES', NULL, 'A empresa possui área de recebimento separada da expedição?', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 2, 'CHECKLIST', 'INSTALAÇÕES', NULL, 'Existe área identificada para produtos não conformes?', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 3, 'CHECKLIST', 'INSTALAÇÕES', NULL, 'Produtos perigosos ficam em área própria e sinalizada?', 'Verificar sinalização e contenção'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 4, 'CHECKLIST', 'PRODUTOS', NULL, 'Os produtos certificados trazem o selo de identificação da conformidade?', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 5, 'CHECKLIST', 'PRODUTOS', NULL, 'As embalagens seguem a especificação aprovada?', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 6, 'CHECKLIST', 'GARANTIA DA QUALIDADE', NULL, 'Os ensaios de rotina são feitos em cada lote?', 'Conferir laudos dos últimos lotes'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 7, 'CHECKLIST', 'GARANTIA DA QUALIDADE', NULL, 'Os instrumentos de ensaio estão calibrados?', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 8, 'CHECKLIST', 'GARANTIA DA QUALIDADE', NULL, 'Reclamações sobre produtos certificados são registradas?', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 9, 'CHECKLIST', 'PESSOAL', NULL, 'Há responsável técnico designado?', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 10, 'CHECKLIST', 'PESSOAL', NULL, 'Os operadores são treinados nos requisitos de certificação?', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 11, 'CHECKLIST', 'DOCUMENTOS', NULL, 'O certificado de conformidade está vigente?', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-104'), 12, 'CHECKLIST', 'DOCUMENTOS', NULL, 'Os registros de produção ficam disponíveis para a auditoria do organismo certificador?', NULL);

-- ---------------------------------------------------------------- 5. Housekeeping (campo Local, sem referência nem DOC's)
INSERT INTO template_checklist (codigo, revisao, titulo, tipo, nome, ordem, rotulo_auditoria, rotulo_referencia, usa_processo, usa_documentos, usa_local, opcoes_conformidade, exige_registro_nc, escopo_padrao, sugestoes_evidencia, rodape) VALUES
('FQ-105', '00', 'Checklist de Housekeeping', 'AUDITORIA', 'Housekeeping', 5, 'Auditoria Housekeeping', NULL, FALSE, FALSE, TRUE,
 'C:C:Conforme|NC:NC:Não Conforme|NA:NA:Não Aplicável', FALSE,
 NULL, NULL,
 'FQ-105 - Rev. 00 - Data: 05/01/2026');

INSERT INTO pergunta (template_id, numero, secao, grupo, clausula, texto) VALUES
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 1, 'CHECKLIST', 'LIMPEZA', NULL, 'Piso e bancadas limpos, sem resíduos acumulados'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 2, 'CHECKLIST', 'LIMPEZA', NULL, 'Nenhum vazamento ou derramamento visível'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 3, 'CHECKLIST', 'LIMPEZA', NULL, 'Máquinas limpas ao fim do turno'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 4, 'CHECKLIST', 'ORGANIZAÇÃO', NULL, 'Ferramentas guardadas no quadro de sombra'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 5, 'CHECKLIST', 'ORGANIZAÇÃO', NULL, 'Materiais identificados e no endereço correto'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 6, 'CHECKLIST', 'ORGANIZAÇÃO', NULL, 'Sem objetos pessoais ou itens fora de uso na área'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 7, 'CHECKLIST', 'SEGURANÇA', NULL, 'Extintores desobstruídos e com inspeção em dia'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 8, 'CHECKLIST', 'SEGURANÇA', NULL, 'Rotas de fuga e faixas de circulação livres'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 9, 'CHECKLIST', 'SEGURANÇA', NULL, 'EPIs disponíveis e em bom estado'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 10, 'CHECKLIST', 'DESCARTE', NULL, 'Coletores identificados por tipo de resíduo'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 11, 'CHECKLIST', 'DESCARTE', NULL, 'Coletores esvaziados antes de transbordar'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 12, 'CHECKLIST', 'DESCARTE', NULL, 'Resíduos perigosos segregados e rotulados'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 13, 'CHECKLIST', 'ÁREAS COMUNS', NULL, 'Refeitório e copa limpos e organizados'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 14, 'CHECKLIST', 'ÁREAS COMUNS', NULL, 'Vestiários sem roupas ou calçados largados'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-105'), 15, 'CHECKLIST', 'ÁREAS COMUNS', NULL, 'Quadros de aviso atualizados');

-- ---------------------------------------------------------------- 6. Inspeção de OP's (tipo INSPECAO_OP)
-- A escala real da inspeção (C/NC/NA/NV/NI/NP e S/N/P no EPI) fica no código (InspecaoValores);
-- opcoes_conformidade aqui só serve à tela genérica de formulário.
INSERT INTO template_checklist (codigo, revisao, titulo, tipo, nome, ordem, rotulo_auditoria, rotulo_referencia, usa_processo, usa_documentos, usa_local, opcoes_conformidade, exige_registro_nc, escopo_padrao, sugestoes_evidencia, rodape) VALUES
('FQ-106', '01', 'Inspeção de Ordens de Produção', 'INSPECAO_OP', 'Inspeção de OP''s', 6, 'Inspeção de OP''s', NULL, FALSE, FALSE, FALSE,
 'C:C:Conforme|NC:N/C:Não Conforme|NA:N/A:Não se Aplica', FALSE,
 NULL, NULL,
 'FQ-106 - Rev. 01 - Data: 12/05/2026');

INSERT INTO pergunta (template_id, numero, secao, grupo, clausula, texto, escala) VALUES
((SELECT id FROM template_checklist WHERE codigo = 'FQ-106'), 1, 'CHECKLIST', NULL, NULL, 'Aparência do produto', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-106'), 2, 'CHECKLIST', NULL, NULL, 'Identificação e rotulagem', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-106'), 3, 'CHECKLIST', NULL, NULL, 'Integridade da embalagem', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-106'), 4, 'CHECKLIST', NULL, NULL, 'Lote da matéria-prima', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-106'), 5, 'CHECKLIST', NULL, NULL, 'Zero da balança', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-106'), 6, 'CHECKLIST', NULL, NULL, 'Peso líquido', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-106'), 7, 'CHECKLIST', NULL, NULL, 'EPI''s', 'EPI'),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-106'), 8, 'CHECKLIST', NULL, NULL, 'Vedação do fechamento', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-106'), 9, 'CHECKLIST', NULL, NULL, 'Dimensões conforme especificação', NULL),
((SELECT id FROM template_checklist WHERE codigo = 'FQ-106'), 10, 'CHECKLIST', NULL, NULL, 'Registro na ordem de produção', NULL);
