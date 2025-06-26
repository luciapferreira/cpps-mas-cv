# Cyber-Physical Production System with Computer Vision Integration

This project implements a Cyber-Physical Production System (CPPS) using Multi-Agent Systems (MAS) with computer vision for defect detection in a manufacturing environment.

## Features

### 1. Agent System
- Product lifecycle management
- Resource allocation  
- Transportation scheduling* 
- Production order management

### 2. Quality Inspection
- Defect detection using YOLOv8
- Position-based defect classification
- REST API for inspection 

### 3. Defect Recovery
- Routing based on defect position
- Re-processing workflow
- Station selection logic

## System Architecture

### Agent & Vision Workflow

```mermaid
graph TD
    OA[Order Agent] -->|Creates| PA[Product Agents]
    PA -->|Negotiates| RA[Resource Agents]
    PA -->|Requests| TA[Transport Agents]
    RA -->|Controls| GS[Glue Stations]
    RA -->|Controls| QC[Quality Control]
    QC -->|Uses| CV[Vision API]
    CV -->|Runs| YOLO[YOLOv8]
    TA -->|Controls| AGV[AGV Movement]
    QC -->|Defect Found| RL[Recovery Logic]
    RL -->|Routes| GS
```

### Production Workflow

```mermaid
sequenceDiagram
    OrderAgent->>ProductAgent: Create product
    ProductAgent->>ResourceAgent: Reserve station
    ProductAgent->>TransportAgent: Request transport
    TransportAgent->>ResourceAgent: Move product
    ResourceAgent->>ProductAgent: Execute skill
    ProductAgent->>QualityControl: Request inspection
    QualityControl->>VisionAPI: Send image
    VisionAPI->>QualityControl: Return result
    alt Defect found
        QualityControl->>ProductAgent: NOK with position
        ProductAgent->>RecoveryLogic: Route to station
    else No defect
        QualityControl->>ProductAgent: OK
    end
    ProductAgent->>OrderAgent: Notify completion
```