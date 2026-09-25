# V7.51 — Sincronização em nuvem

Esta etapa prepara o Controle Queijos para que dois ou mais Androids usem os mesmos dados através do Supabase.

## Arquivo SQL
Execute `supabase_nuvem_v751.sql` no SQL Editor do projeto Supabase.

## Segurança
As tabelas usam Row Level Security (RLS) e cada registro fica vinculado ao usuário autenticado do Supabase. O mesmo login em dois aparelhos permite acessar os mesmos dados.

## Próxima etapa
A V7.51 do Android receberá:
- login no Supabase;
- configuração segura da URL/chave pública;
- leitura dos dados da nuvem;
- envio das alterações;
- sincronização entre aparelhos;
- tratamento de conflitos para não sobrescrever alterações recentes sem controle.

Não coloque a chave `service_role` dentro do APK. O aplicativo deverá usar somente a chave pública/anon e autenticação do Supabase.
