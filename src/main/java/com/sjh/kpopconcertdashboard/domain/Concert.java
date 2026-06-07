package com.sjh.kpopconcertdashboard.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String kopisId;

    private String title;

    private LocalDate startDate;

    private LocalDate endDate;

    private String venueName;

    private String genre;

    private String region;

    private String posterUrl;

    private String state;

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Concert concert)) return false;

        return Objects.equals(kopisId, concert.kopisId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(kopisId);
    }
}
