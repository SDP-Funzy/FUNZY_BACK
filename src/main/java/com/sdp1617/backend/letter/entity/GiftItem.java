package com.sdp1617.backend.letter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 두들픽 선물 후보 1개. 받는 쪽 선물 항목 이모지(gift_item_emoji_reactions.gift_item_id)가 이 ID를 가리킨다 (#82). */
@Getter
@Entity
@Table(name = "gift_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GiftItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "letter_id", nullable = false)
    private Letter letter;

    @Column(name = "item_order", nullable = false)
    private int itemOrder;

    @Column(nullable = false, length = 30)
    private String name;

    GiftItem(Letter letter, int itemOrder, String name) {
        this.letter = letter;
        this.itemOrder = itemOrder;
        this.name = name;
    }
}
