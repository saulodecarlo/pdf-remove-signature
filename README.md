# PDF Remove Signature — HTTP API (iText + AWS S3 + Docker)

Serviço HTTP para remover assinaturas digitais de arquivos PDF armazenados no **AWS S3**, usando **iText 9**, **Spring Boot 3** e **AWS SDK v2**.

## Estrutura do Projeto

```
├── src/main/java/com/pdftools/
│   ├── Application.java                  # Spring Boot main
│   ├── config/
│   │   └── AwsConfig.java                # S3Client bean (role vs static keys)
│   ├── controller/
│   │   └── PdfController.java            # POST /api/v1/remove-signature
│   └── service/
│       ├── PdfSignatureService.java       # Lógica iText para remover assinaturas
│       └── S3Service.java                 # Download/Upload S3
├── src/main/resources/
│   └── application.yml
├── .env                                   # Variáveis AWS + PRODUCTION
├── Dockerfile                             # Multi-stage build
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Como Usar

### 1. Configure o `.env`

```env
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=your-access-key-id
AWS_SECRET_ACCESS_KEY=your-secret-access-key
AWS_S3_ENDPOINT=
PRODUCTION=false
SERVER_PORT=8090
```

> **PRODUCTION=true** → usa IAM Role (ignora access key / secret key). Ideal para EC2, ECS, EKS e Lambda.

### 2. Build e start

```bash
docker compose build
docker compose up -d
```

### 3. Enviar requisição

```bash
curl -X POST http://localhost:8090/api/v1/remove-signature \
  -H "Content-Type: application/json" \
  -d '{
    "bucket": "meu-bucket",
    "path": "docs/assinado.pdf"
  }'
```

### Resposta

```json
{
  "output": "docs/sem-certificado/assinado.pdf"
}
```

O arquivo sem assinatura é salvo no **mesmo bucket**, dentro do sub-diretório `sem-certificado/` relativo ao path original.

### Exemplos de path

| Input path | Output path |
|---|---|
| `assinado.pdf` | `sem-certificado/assinado.pdf` |
| `docs/assinado.pdf` | `docs/sem-certificado/assinado.pdf` |
| `empresa/2026/contrato.pdf` | `empresa/2026/sem-certificado/contrato.pdf` |

### 4. Health check

```bash
curl http://localhost:8090/actuator/health
```

## O que o serviço faz

1. **Recebe requisição HTTP** com `bucket` e `path`
2. **Baixa o PDF** do S3
3. **Remove campos de assinatura** do AcroForm
4. **Remove anotações Widget** de assinatura de cada página
5. **Limpa SigFlags** do AcroForm
6. **Faz upload** do PDF limpo para `{path}/sem-certificado/{filename}` no mesmo bucket
7. **Retorna o path** do arquivo de saída

## Configuração AWS

| Variável | Descrição | Padrão |
|---|---|---|
| `AWS_REGION` | Região AWS | `us-east-1` |
| `AWS_ACCESS_KEY_ID` | Access Key (ignorado se PRODUCTION=true) | — |
| `AWS_SECRET_ACCESS_KEY` | Secret Key (ignorado se PRODUCTION=true) | — |
| `AWS_S3_ENDPOINT` | Endpoint customizado (ex: LocalStack) | — |
| `PRODUCTION` | `true` = usa IAM Role, `false` = usa static keys | `false` |
| `SERVER_PORT` | Porta exposta do container | `8090` |

## Requisitos

- Docker
- Docker Compose

## Build manual (sem Docker)

```bash
mvn clean package -DskipTests
java -jar target/pdf-remove-signature-1.0.0.jar
```

## Tecnologias

- Java 21
- Spring Boot 4.0.2
- iText 9.5.0
- AWS SDK v2 (S3 + STS)
- Docker (multi-stage build)

## Licença

Este projeto está licenciado sob a **GNU Affero General Public License v3.0 (AGPL-3.0)** — veja o arquivo [LICENSE](LICENSE) para mais detalhes. O iText 9 é distribuído sob a AGPL, o que requer que qualquer software que o utilize e seja disponibilizado via rede também seja código aberto sob a mesma licença.

