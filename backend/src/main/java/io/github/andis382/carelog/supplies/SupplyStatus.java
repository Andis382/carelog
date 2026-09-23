package io.github.andis382.carelog.supplies;

public enum SupplyStatus {
    OK,
    RUNNING_LOW,
    OUT;

    public boolean needsBuying() {
        return this != OK;
    }
}
