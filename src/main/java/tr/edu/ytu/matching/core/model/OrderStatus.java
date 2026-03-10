package tr.edu.ytu.matching.core.model;

public enum OrderStatus {
    PENDING,            // Bekliyor (Tahtada)
    PARTIALLY_FILLED,   // Kısmen eşleşti
    FILLED,             // Tamamen eşleşti
    CANCELED            // İptal edildi
}