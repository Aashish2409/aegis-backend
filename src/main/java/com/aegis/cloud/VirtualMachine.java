package com.aegis.cloud;

public record VirtualMachine(
        String id,
        String name,
        String region,
        String status,
        String instanceType
) {}
