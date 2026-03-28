package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@DiscriminatorValue("REVENU")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Revenu extends Transaction {

    @Override
    public void calculerTotal() {
        // Implementation logic
    }

}
