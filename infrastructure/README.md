# Infrastructure roadmap

This directory contains boundaries only; no Terraform resources are implemented or provisioned. Future AWS infrastructure is expected to include ECS Fargate, ECR, an ALB, CloudWatch, Secrets Manager and PostgreSQL, with RDS proposed. Production TLS, authentication, networking, IAM and secret delivery require design review.

Modules: network (VPC and connectivity), ecr (container repositories), ecs (services and tasks), alb (routing and TLS), database (database infrastructure), iam (least-privilege roles), monitoring (logs and metrics). Environments dev and prod compose reviewed modules separately. Do not run apply or provision cloud resources without explicit authorization.
