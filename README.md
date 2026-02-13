````markdown
# PDF Remove Signature — HTTP API (iText + AWS S3 + Docker)

HTTP service to remove digital signatures from PDF files stored in **AWS S3**, using **iText 9**, **Spring Boot 4**, and **AWS SDK v2**.

## Project Structure

```
├── src/main/java/com/pdftools/
│   ├── Application.java                  # Spring Boot main
│   ├── config/
│   │   └── AwsConfig.java                # S3Client bean (role vs static keys)
│   ├── controller/
│   │   └── PdfController.java            # POST /api/v1/remove-signature
│   └── service/
│       ├── PdfSignatureService.java      # iText logic to remove signatures
│       └── S3Service.java                # S3 download/upload
├── src/main/resources/
│   └── application.yml
├── .env                                   # AWS vars + PRODUCTION
├── Dockerfile                             # Multi-stage build
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Usage

### 1. Configure `.env`

```env
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=your-access-key-id
AWS_SECRET_ACCESS_KEY=your-secret-access-key
AWS_S3_ENDPOINT=
PRODUCTION=false
SERVER_PORT=8090
```

> **PRODUCTION=true** → use IAM Role (ignores access key / secret key). Suitable for EC2, ECS, EKS and Lambda.

### 2. Build and start

```bash
docker compose build
docker compose up -d
```

### 3. Send request

```bash
curl -X POST http://localhost:8090/api/v1/remove-signature \
  -H "Content-Type: application/json" \
  -d '{
    "bucket": "my-bucket",
    "path": "docs/signed.pdf"
  }'
```

### Response

```json
{
  "output": "docs/unsigned/signed.pdf"
}
```

The unsigned file is saved in the **same bucket**, under the `unsigned/` subdirectory relative to the original path.

### Path examples

| Input path | Output path |
|---|---|
| `signed.pdf` | `unsigned/signed.pdf` |
| `docs/signed.pdf` | `docs/unsigned/signed.pdf` |
| `company/2026/contract.pdf` | `company/2026/unsigned/contract.pdf` |

### 4. Health check

```bash
curl http://localhost:8090/actuator/health
```

## What the service does

1. **Receives an HTTP request** with `bucket` and `path`
2. **Downloads the PDF** from S3
3. **Removes signature fields** from the AcroForm
4. **Removes Widget annotations** related to signatures from each page
5. **Clears SigFlags** from the AcroForm
6. **Uploads** the cleaned PDF to `{path}/unsigned/{filename}` in the same bucket
7. **Returns the output path**

## AWS configuration

| Variable | Description | Default |
|---|---|---|
| `AWS_REGION` | AWS region | `us-east-1` |
| `AWS_ACCESS_KEY_ID` | Access Key (ignored if PRODUCTION=true) | — |
| `AWS_SECRET_ACCESS_KEY` | Secret Key (ignored if PRODUCTION=true) | — |
| `AWS_S3_ENDPOINT` | Custom endpoint (e.g. LocalStack) | — |
| `PRODUCTION` | `true` = use IAM Role, `false` = use static keys | `false` |
| `SERVER_PORT` | Port exposed by the container | `8090` |

## Requirements

- Docker
- Docker Compose

## Manual build (without Docker)

```bash
mvn clean package -DskipTests
java -jar target/pdf-remove-signature-1.0.0.jar
```

## Technologies

- Java 21
- Spring Boot 4.0.2
- iText 9.5.0
- AWS SDK v2 (S3 + STS)
- Docker (multi-stage build)

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)** — see the [LICENSE](LICENSE) file for details. iText 9 is distributed under the AGPL, which requires that any software using it and made available over a network must also be open-sourced under the same license.


````
