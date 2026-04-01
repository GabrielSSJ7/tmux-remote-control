# Remote Control -- Setup de Produção

Guia completo para configurar o backend Rust no PC e o app Android no celular.

## Pré-requisitos

### PC

- Linux (testado em Arch Linux)
- Rust toolchain (`rustup`)
- tmux
- Porta liberada no roteador

### Celular

- Android 8.0+ (API 26)
- Android SDK para build (ou receber o APK pronto)

---

## 1. Backend Rust

### 1.1 Build

```bash
cd ~/code/remote-control/backend
cargo build --release
```

O binário será gerado em `target/release/remote-control-backend`.

### 1.2 Configuração

```bash
# Copiar config padrão (se ainda não existe)
cp config.toml config.toml.bak

# Editar se necessário
nano config.toml
```

Conteúdo do `config.toml`:

```toml
[server]
host = "0.0.0.0"
port = 48322

[auth]
token = ""

[terminal]
scrollback_lines = 10000
default_shell = "/bin/bash"
```

Na primeira execução, se `token` estiver vazio, o backend gera um token automaticamente e salva no arquivo. Anote esse token -- ele será usado no app.

### 1.3 Primeira execução

```bash
cd ~/code/remote-control/backend
./target/release/remote-control-backend
```

Saída esperada:

```
Generated new auth token: <64 caracteres hex>
{"timestamp":"...","level":"INFO","message":"Listening on 0.0.0.0:48322"}
```

Copie o token. Ele já foi salvo no `config.toml`.

### 1.4 Verificar funcionamento

```bash
# Health check
curl http://localhost:48322/health
# Esperado: ok

# Listar sessões (com token)
TOKEN="<seu_token_aqui>"
curl http://localhost:48322/sessions -H "Authorization: Bearer $TOKEN"
# Esperado: []

# Criar sessão
curl -X POST http://localhost:48322/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"test"}'

# Verificar no tmux
tmux list-sessions
# Esperado: test: ...
```

### 1.5 Systemd service (execução permanente)

```bash
sudo nano /etc/systemd/system/remote-control.service
```

```ini
[Unit]
Description=Remote Control Backend
After=network.target

[Service]
Type=simple
User=gluz
WorkingDirectory=/home/gluz/code/remote-control/backend
ExecStart=/home/gluz/code/remote-control/backend/target/release/remote-control-backend
Restart=always
RestartSec=5
Environment=RUST_LOG=info

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable remote-control
sudo systemctl start remote-control

# Verificar status
sudo systemctl status remote-control

# Ver logs
journalctl -u remote-control -f
```

---

## 2. Rede

### 2.1 Firewall local

```bash
# iptables
sudo iptables -A INPUT -p tcp --dport 48322 -j ACCEPT
sudo iptables-save | sudo tee /etc/iptables/iptables.rules

# ou ufw
sudo ufw allow 48322/tcp
```

### 2.2 Port forwarding no roteador

Acesse o painel do roteador (geralmente `192.168.1.1`) e configure:

| Campo | Valor |
|-------|-------|
| Porta externa | 48322 |
| Porta interna | 48322 |
| IP interno | IP do seu PC (ex: 192.168.1.100) |
| Protocolo | TCP |

Para descobrir o IP interno do PC:

```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
```

### 2.3 Testar acesso externo

No celular (usando dados móveis, não WiFi):

```
http://SEU_IP_FIXO:48322/health
```

Se retornar `ok`, a rede está configurada.

### 2.4 IP fixo do PC na rede local

Para garantir que o PC sempre tenha o mesmo IP interno, configure IP estático ou reserve o IP no DHCP do roteador.

Exemplo com `systemd-networkd`:

```bash
sudo nano /etc/systemd/network/20-wired.network
```

```ini
[Match]
Name=enp*

[Network]
Address=192.168.1.100/24
Gateway=192.168.1.1
DNS=8.8.8.8
```

---

## 3. TLS (obrigatório para produção)

> **AVISO: TLS é obrigatório para uso em produção.**
> Sem TLS, o token de autenticação é transmitido em texto simples e pode ser capturado por qualquer pessoa na rede (ex: roteador comprometido, Wi-Fi público, ISP). Nunca exponha o backend na internet sem TLS.

### Opção A: TLS nativo (recomendado)

O backend suporta TLS diretamente via rustls. Não é necessário nenhum proxy.

**Gerar certificado autoassinado:**

```bash
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -sha256 -days 365 -nodes \
  -subj "/CN=remote-control"
```

Mova os arquivos para um local seguro (ex: `~/certs/`) e configure no `config.toml`:

```toml
[server]
host = "0.0.0.0"
port = 48322

[auth]
token = ""

[terminal]
scrollback_lines = 10000
default_shell = "/bin/bash"

[tls]
cert_path = "/home/gluz/certs/cert.pem"
key_path = "/home/gluz/certs/key.pem"
```

**Verificar TLS ativo:**

```bash
openssl s_client -connect localhost:48322 -showcerts
```

Se a conexão for estabelecida e exibir o certificado, o TLS está funcionando.

**No app Android:** atualizar Server URL para usar `https://` e `wss://`:

```
https://SEU_IP_FIXO:48322/
```

**Atenção: certificados autoassinados no Android**

O Android rejeita certificados autoassinados por padrão. Para usar um certificado autoassinado, adicione o arquivo `cert.pem` ao projeto Android:

1. Copie `cert.pem` para `android/app/src/main/res/raw/cert.pem`
2. Referencie no `network_security_config.xml` (já configurado no projeto)
3. Reconstrua e reinstale o APK

Alternativa sem essa configuração manual: use um certificado CA-válido via Caddy (Opção B).

### Opção B: Caddy como reverse proxy

Alternativa ao TLS nativo. Útil se você já tem um domínio e quer Let's Encrypt automático.

```bash
sudo pacman -S caddy  # Arch
# ou: sudo apt install caddy  # Debian/Ubuntu

sudo nano /etc/caddy/Caddyfile
```

```
meupc.exemplo.com {
    reverse_proxy localhost:48322
}
```

```bash
sudo systemctl enable caddy
sudo systemctl start caddy
```

Caddy gera certificados TLS automaticamente via Let's Encrypt. No app, usar `https://meupc.exemplo.com/` como Server URL. Certificados Let's Encrypt são aceitos pelo Android sem configuração adicional.

Pré-requisito: um domínio apontando para seu IP fixo (DDNS ou DNS normal).

---

## 4. App Android

### 4.1 Build do APK

```bash
cd ~/code/remote-control/android
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleRelease
```

O APK estará em:

```
app/build/outputs/apk/release/app-release-unsigned.apk
```

### 4.2 Assinar o APK (necessário para instalar)

```bash
# Gerar keystore (uma vez só)
keytool -genkey -v -keystore ~/remote-control.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias remote-control \
  -storepass SUA_SENHA_AQUI \
  -keypass SUA_SENHA_AQUI \
  -dname "CN=Remote Control, O=Personal"

# Assinar
cd app/build/outputs/apk/release/
apksigner sign \
  --ks ~/remote-control.keystore \
  --ks-key-alias remote-control \
  --out app-release.apk \
  app-release-unsigned.apk

# Verificar assinatura
apksigner verify app-release.apk
```

Alternativa: usar debug APK para testes:

```bash
./gradlew assembleDebug
# APK em: app/build/outputs/apk/debug/app-debug.apk
```

### 4.3 Instalar no celular

Via USB:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Ou transferir o APK pro celular (Bluetooth, email, drive) e instalar manualmente (habilitar "fontes desconhecidas").

### 4.4 Configurar o app

1. Abrir "Remote Control"
2. Tela inicial mostra "Server not configured" → tocar "Open Settings"
3. **Server URL**: `http://SEU_IP_FIXO:48322/` (ou `https://` se usando TLS)
4. **Auth Token**: colar o token de 64 caracteres do `config.toml`
5. Voltar → tela de Sessions

---

## 5. Teste completo

### 5.1 Criar e usar sessão

1. No app, tocar `+` para criar sessão
2. Tocar na sessão criada → terminal abre
3. Digitar `echo hello` → ver output
4. Testar extra keys: Tab, Ctrl+C, setas

### 5.2 Persistência PC → Celular

No PC:

```bash
# Listar sessões do backend
tmux list-sessions

# Attach numa sessão criada pelo app
tmux attach -t rc-xxxxxxxx

# Rodar algo
htop
```

No celular: abrir a mesma sessão → ver htop rodando.

### 5.3 Biblioteca de comandos

1. No terminal, tocar no botão flutuante (play)
2. Tocar `+` para adicionar comando
3. Preencher: Nome="Status", Comando="git status", Categoria="git"
4. Tocar no comando → ele é enviado ao terminal

### 5.4 Reconexão

1. Desligar WiFi do celular
2. Banner "Disconnected - reconnecting..." aparece
3. Ligar WiFi → reconecta automaticamente
4. Output do terminal é restaurado (buffer do tmux)

---

## 6. Manutenção

### Atualizar backend

```bash
cd ~/code/remote-control/backend
git pull
cargo build --release
sudo systemctl restart remote-control
```

### Atualizar app

```bash
cd ~/code/remote-control/android
git pull
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Backup do token

O token está em `backend/config.toml`. Se perder, gere um novo deletando o valor e reiniciando o backend. Lembre de atualizar no app.

### Logs do backend

```bash
# Tempo real
journalctl -u remote-control -f

# Últimas 100 linhas
journalctl -u remote-control -n 100

# Filtrar erros
journalctl -u remote-control | grep -i error
```

### Verificar sessões tmux ativas

```bash
tmux list-sessions
```

### Matar sessão manualmente

```bash
tmux kill-session -t <nome>
```
