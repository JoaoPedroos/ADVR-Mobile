# ADVR — Acompanhamento Processual Simplificado

Aplicativo Android nativo (Java + XML + SQLite)

---

## Como Executar o Projeto

1. Clone ou baixe este repositório e abra a pasta raiz **`ADVR`** no **Android Studio**.
2. Aguarde a sincronização do **Gradle** finalizar.
3. Conecte um dispositivo Android ou inicie um emulador e clique em **Run (`Shift + F10`)**.

> **Dica importante para testes:** O banco de dados SQLite (`advr.db`) realiza a carga automática dos usuários e processos fictícios na primeira execução (`onCreate`).

---

## Usuários de Teste Pré-Cadastrados

Todas as contas abaixo já vêm configuradas no banco de dados local com a senha padrão **`Teste123!`**:

| Perfil | Nome | E-mail de Acesso | CPF | Senha |
| :--- | :--- | :--- | :--- | :--- |
| **Administrador** | Administrador ADVR | `admin@advr.com` | `52998224725` | `Teste123!` |
| **Advogado** | Dr. Ana Paula | `ana@advr.com` | `12345678909` | `Teste123!` |
| **Cliente** | Mariana Silva | `mariana@teste.com` | `11144477735` | `Teste123!` |
