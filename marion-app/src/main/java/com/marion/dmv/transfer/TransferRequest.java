package com.marion.dmv.transfer;

public record TransferRequest(
        String question,
        String vehicleVin,
        String originState,
        String county,
        String transferType
) {}
