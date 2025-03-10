package com.likelion.beshop.service;

import com.likelion.beshop.dto.OrderDto;
import com.likelion.beshop.dto.OrderHistDto;
import com.likelion.beshop.dto.OrderItemDto;
import com.likelion.beshop.entity.*;
import com.likelion.beshop.repository.ItemImgRepository;
import com.likelion.beshop.repository.ItemRepository;
import com.likelion.beshop.repository.MemberRepository;
import com.likelion.beshop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityNotFoundException;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {
    private  final ItemRepository itemRepository;
    private  final MemberRepository memberRepository;
    private  final OrderRepository orderRepository;
    private  final ItemImgRepository itemImgRepository;
    public  Long order(OrderDto orderDto, String email){ // 파라미터로는 OrderDto와 이메일을 받아온다.

        Item item = itemRepository.findById(orderDto.getItemId()) // 주문할 상품 조회
                .orElseThrow(EntityNotFoundException::new); //예외처리

        Member member = memberRepository.findByEmail(email); // 현재 회원의 이메일 정보로 회원 정보 조회

        List<OrderItem> orderItemList = new ArrayList<>(); // 주문 상품 리스트 생성
        OrderItem orderItem = OrderItem.createOrderItem(item, orderDto.getCount()); // 주문할 상품 엔티티와 주문 수량으로 주문 상품 생성
        orderItemList.add(orderItem); // 생성한 주문 상품을 주문 상품 리스트에 추가
        Order order = Order.createOrder(member, orderItemList); // 회원 정보와 주문 상품 리스트 정보로 주문 생성
        orderRepository.save(order); // 생성한 주문 엔티티 저장

        return  order.getCode();
    }

    // 주문 목록을 조회하는 로직
    @Transactional(readOnly = true)
    public Page<OrderHistDto> getOrderList(String email, Pageable pageable) { // 파라미터로는 이메이로가 페이징 처리를 위한 Pageable을 받아온다.
        
        // 메소드 구성
        List<Order> orders = orderRepository.findOrders(email, pageable); // 회원 이메일과 페이징 조건으로 주문 목록 조회 
        Long totalCount = orderRepository.countOrder(email); // 이메일로 주문 횟수 조회

        List<OrderHistDto> orderHistDtos = new ArrayList<>(); // 주문 내역을 받아올 리스트 생성

        for (Order order : orders) {
            // 주문 리스트를 돌면서 페이지에 전달할 이력 DTO 생성
            OrderHistDto orderHistDto = new OrderHistDto(order);
            List<OrderItem> orderItems = order.getOrderItems(); // 주문 상품 리스트를 리스트로 받아오기
            for (OrderItem orderItem : orderItems) {
                // 받아온 상품들의 대표 이미지 조회
                ItemImg itemImg = itemImgRepository.findByItemIdAndRepImage
                        (orderItem.getItem().getId(), "Y");
                // 주문 상품 DTO 생성
                OrderItemDto orderItemDto =
                        new OrderItemDto(orderItem, itemImg.getImagePath());
                // 해당 DTO를 주문 이력 DTO의 주문 상품에 추가
                orderHistDto.addOrderItemDto(orderItemDto);
            }

            orderHistDtos.add(orderHistDto); // 주문 이력 DTO를 주문 내역 리스트에 추가
        }

        return new PageImpl<OrderHistDto>(orderHistDtos, pageable, totalCount); // 반환값은 PageImpl을 이용해 페이지 구현 객체를 생성한 값
    }

    // 주문한 사용자 = 현재 로그인한 사용자인지 확인하는 메소드
    @Transactional(readOnly = true) // 읽기 전용 설정
    public boolean validateOrder(Long orderId, String email){ // 파라미터로 주문 번호와 이메일 받아오기
        Member curMember = memberRepository.findByEmail(email); // 이메일로 사용자 조회
        Order order = orderRepository.findById(orderId) // 주문 번호로 주문 조회
                .orElseThrow(EntityNotFoundException::new);
        Member savedMember = order.getMember(); // 주문한 사용자 조회

        // 사용자의 이메일과 주문한 사용자의 이메일이 같은지 확인하고 이에 따라 참/거짓 반환
        if(!StringUtils.equals(curMember.getEmail(), savedMember.getEmail())){
            return false;
        }

        return true;
    }

    // 주문 취소 메소드
    public void cancelOrder(Long orderId){ // 파라미터로 주문 번호 받아오기
        Order order = orderRepository.findById(orderId)
                .orElseThrow(EntityNotFoundException::new);
        order.cancelOrder();
        // 해당 주문 취소 메소드 호출
        //→ 주문 취소 상태로 변경 시, 변경 감지 기능에 의해 트랜잭션 끝날 때 update 쿼리 실행
    }

    public Long orders(List<OrderDto> orderDtoList, String email){ // 파라미터로는 OrderDto와 이메일을 받아온다.

        Member member = memberRepository.findByEmail(email); // email로 회원 조회해 새로운 member 객체에 저장

        List<OrderItem> orderItemList = new ArrayList<>(); // 새로운 orderItemList 생성

        // orderDtoList 돌면서 orderDto로부터 상품 아이디 받아 상품 조회해 item 객체에 저장(예외처리 해주기)
        for(OrderDto orderDto : orderDtoList){
            Item item = itemRepository.findById(orderDto.getItemId())
                    .orElseThrow(EntityNotFoundException::new);
            // 새로운 orderItem 객체 생성해 orderItemList에 추가(OrderItem의 createOrderItem 메서드 이용)
            OrderItem orderItem = OrderItem.createOrderItem(item, orderDto.getCount()); // 주문할 상품 엔티티와 주문 수량으로 주문 상품 생성
            orderItemList.add(orderItem); // 생성한 주문 상품을 주문 상품 리스트에 추가
        }
        // Order의 createOrder 이용하여 orderItemList로 주문 생성하기
        Order order = Order.createOrder(member, orderItemList); // 회원 정보와 주문 상품 리스트 정보로 주문 생성
        orderRepository.save(order); // orderRepository 메서드 이용하여 order 저장

        return  order.getCode(); // order 객체 아이디 반환
    }
}