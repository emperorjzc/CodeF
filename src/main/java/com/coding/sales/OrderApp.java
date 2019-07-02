package com.coding.sales;

import com.coding.sales.input.OrderCommand;
import com.coding.sales.input.OrderItemCommand;
import com.coding.sales.input.PaymentCommand;
import com.coding.sales.output.DiscountItemRepresentation;
import com.coding.sales.output.OrderItemRepresentation;
import com.coding.sales.output.OrderRepresentation;
import com.coding.sales.output.PaymentRepresentation;

import java.io.*;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 销售系统的主入口
 * 用于打印销售凭证
 */
public class OrderApp {
    private List<OrderItemRepresentation> orderItems = new ArrayList<OrderItemRepresentation>();
    private List<DiscountItemRepresentation> discounts = new ArrayList<DiscountItemRepresentation>();
    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("参数不正确。参数1为销售订单的JSON文件名，参数2为待打印销售凭证的文本文件名.");
        }

        String jsonFileName = args[0];
        String txtFileName = args[1];

        String orderCommand = FileUtils.readFromFile(jsonFileName);
        OrderApp app = new OrderApp();
        String result = app.checkout(orderCommand);
        FileUtils.writeToFile(result, txtFileName);
    }

    public String checkout(String orderCommand) {
        OrderCommand command = OrderCommand.from(orderCommand);
        OrderRepresentation result = checkout(command);
        
        return result.toString();
    }

    OrderRepresentation checkout(OrderCommand command) {
        String goodsId = "";//商品编号
        BigDecimal goodsAmount= new BigDecimal(0);//商品数量
        List<OrderItemCommand> goods = command.getItems();//购买商品
        List discountCards = new ArrayList();
        discountCards = (List)command.getDiscounts();//打折券
        String customerName="",oldMemberType="";//客户名 客户等级
        double integralMultiple=1.0;//积分倍数
        int integral=0,totalintegral=0;//客户积分,消费后总积分
        double totalPaySum=0;//总消费金额
        double totalDiscountPrice=0;//优惠金额

        //1.获取客户信息
        String[] customes = getCustomerInfo(command);
        customerName = customes[0];
        oldMemberType = customes[1];
        integral = Integer.parseInt(customes[2]);

        //2.解析购买商品信息，获取购买订单，消费总金额，优惠总金额
        Map goodsmap = getGoodsInfo(command);
        totalPaySum = Double.parseDouble(goodsmap.get("totalPaySum").toString());//总金额
        totalDiscountPrice = Double.parseDouble((String)goodsmap.get("totalDiscountPrice").toString());//优惠金额
        BigDecimal mustPay = new BigDecimal(totalPaySum-totalDiscountPrice);//应付金额

        //3.获取支付信息
        List<PaymentRepresentation> payments = new ArrayList<PaymentRepresentation>();
        List<PaymentCommand> paymentCommand = command.getPayments();
        for(int p=0;p<paymentCommand.size();p++){
            String type = paymentCommand.get(p).getType();
            BigDecimal amount = paymentCommand.get(p).getAmount();
            PaymentRepresentation paymentRepresentation =new PaymentRepresentation(type,amount);
            payments.add(paymentRepresentation);
        }

        //4.计算客户等级
        //积分计算倍数
        Map customerMap = getCustomerlevel(oldMemberType,totalPaySum,totalDiscountPrice,mustPay,integral);//信客户等级
        String newMemberType = customerMap.get("newMemberType").toString();
        totalintegral = Integer.parseInt(customerMap.get("totalintegral").toString());
        int memberPointsIncreased = totalintegral-integral;

        String createTime = command.getCreateTime();//转化创建时间为date类型
        Date date = new Date();
        try {
            DateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:ss:dd");
            date =  f.parse(createTime);
        }catch (Exception e){

        }

        BigDecimal totalPaySumBig = new BigDecimal(totalPaySum);
        BigDecimal totalDiscountPriceBig = new BigDecimal(totalDiscountPrice);

        //5.打印
        OrderRepresentation result = new OrderRepresentation(command.getOrderId(),date,command.getMemberId(),customerName,oldMemberType,newMemberType,memberPointsIncreased,
                totalintegral,orderItems,totalPaySumBig,discounts,totalDiscountPriceBig,mustPay,payments,discountCards);


        return result;
    }

    private Map getCustomerlevel(String oldMemberType,double totalPaySum,double totalDiscountPrice,BigDecimal mustPay,int integral){
        double integralMultiple=1;//积分倍数
        if("普卡".equals(oldMemberType)){
            integralMultiple=1;
        }else if("金卡".equals(oldMemberType)){
            integralMultiple=1.5;
        }else if("白金卡".equals(oldMemberType)){
            integralMultiple=1.8;
        }else{
            integralMultiple=2;
        }
        //总消费积分 = 总消费金额*积分倍数+客户当前积分
        int totalintegral = (int) Double.parseDouble(mustPay.doubleValue()*integralMultiple+integral+"");//总积分

        String newMemberType="普卡";
        if(totalintegral>=100000){
            newMemberType="钻石卡";
        }else if(totalintegral>=50000){
            newMemberType="白金卡";
        }else if(totalintegral>=10000){
            newMemberType="金卡";
        }else {
            newMemberType="普卡";
        }
        Map map = new HashMap();
        map.put("totalintegral",totalintegral);
        map.put("newMemberType",newMemberType);
        return map;
    }
    private  String[]   getCustomerInfo(OrderCommand command){
        //1.获取客户信息
        Properties prop = new Properties();
        String[] customers={};
        //读取属性文件a.properties
        try {
            Reader in = new InputStreamReader(new FileInputStream("D:\\ideaWorkS\\kata-precious_metal_sales_system-master\\src\\main\\java\\customer.properties"), "UTF-8");
            prop.load(in);     ///加载属性列表
            customers= prop.getProperty(command.getMemberId()).split(",");
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return customers;
    }

    private Map getGoodsInfo(OrderCommand command){
        List<OrderItemCommand> goods = command.getItems();//购买商品
        String goodsId = "";//商品编号
        BigDecimal goodsAmount= new BigDecimal(0);//商品数量
        List discountCards = new ArrayList();
        discountCards = (List)command.getDiscounts();//打折券
        double totalPaySum=0;//总消费金额
        double totalDiscountPrice=0;//优惠金额
        //解析购买商品
        for(int i=0;i<goods.size();i++){
            double price = 0,oldPrice=0;
            goodsId = goods.get(i).getProduct();
            goodsAmount =goods.get(i).getAmount();
            double discountSum=0;
            if("001001".equals(goodsId)){//无打折
                oldPrice = 998*goodsAmount.intValue();
                OrderItemRepresentation orderItemRepresentation = new OrderItemRepresentation("001001","世园会五十国钱币册",new BigDecimal(998),new BigDecimal(goodsAmount.intValue()), new BigDecimal(oldPrice));
                orderItems.add(orderItemRepresentation);
            }else if("001002".equals(goodsId)){//可使用9折打折券
                oldPrice = 1380* goodsAmount.intValue();
                if(discountCards!=null){
                    for(int k=0;k<discountCards.size();k++){
                        String discount =(String)discountCards.get(0);
                        if("9折券".equals(discount)){
                            price=oldPrice*0.9;
                            discountSum = oldPrice*0.1;
                        }
                    }
                }
                OrderItemRepresentation orderItemRepresentation = new OrderItemRepresentation("001002","2019北京世园会纪念银章大全40g",new BigDecimal(1380),new BigDecimal(goodsAmount.intValue()), new BigDecimal(oldPrice));
                orderItems.add(orderItemRepresentation);
                if(discountSum>0){
                    DiscountItemRepresentation discountItemRepresentation = new DiscountItemRepresentation("001002","2019北京世园会纪念银章大全40g",new BigDecimal(discountSum));
                    discounts.add(discountItemRepresentation);
                }

            }else if("003001".equals(goodsId)){//可使用95折打折券
                oldPrice = 1580* goodsAmount.intValue();
                if(discountCards!=null){
                    for(int n=0;n<discountCards.size();n++){
                        String discount =(String)discountCards.get(0);
                        if("9折券".equals(discount)){
                            price=oldPrice*0.95;
                            discountSum=oldPrice*0.05;
                        }
                    }
                }
                OrderItemRepresentation orderItemRepresentation = new OrderItemRepresentation("003001","招财进宝",new BigDecimal(1580),new BigDecimal(goodsAmount.intValue()), new BigDecimal(oldPrice));
                orderItems.add(orderItemRepresentation);
                if(discountSum>0){
                    DiscountItemRepresentation discountItemRepresentation = new DiscountItemRepresentation("003001","招财进宝",new BigDecimal(discountSum));
                    discounts.add(discountItemRepresentation);
                }

            }else if("003002".equals(goodsId)){//参与满减：第3件半价，满3送1
                oldPrice = 980* goodsAmount.intValue();
                if(goodsAmount.intValueExact()<3){
                    price = 980* goodsAmount.intValue();
                }else if(goodsAmount.intValue()==3){
                    price = 980*(goodsAmount.intValue()-1)+980*0.5;
                }else if(goodsAmount.intValue()>3){
                    price = 980* (goodsAmount.intValue()-1);
                }
                OrderItemRepresentation orderItemRepresentation = new OrderItemRepresentation("003002","水晶之恋",new BigDecimal(980),new BigDecimal(goodsAmount.intValue()), new BigDecimal(oldPrice));
            }else if("002002".equals(goodsId)){//参与满减：每满2000减30，每满1000减10
                oldPrice = 998* goodsAmount.intValue();

                if(oldPrice>=2000){
                    price = oldPrice-30;
                    discountSum=30;
                }else if(oldPrice>1000){
                    price = oldPrice-10;
                    discountSum=10;
                }
                OrderItemRepresentation orderItemRepresentation = new OrderItemRepresentation("002002","中国经典钱币套装",new BigDecimal(998),new BigDecimal(goodsAmount.intValue()), new BigDecimal(oldPrice));
                orderItems.add(orderItemRepresentation);
                if(discountSum>0) {
                    DiscountItemRepresentation discountItemRepresentation = new DiscountItemRepresentation("002002", "中国经典钱币套装", new BigDecimal(discountSum));
                    discounts.add(discountItemRepresentation);
                }
            }else if("002001".equals(goodsId)){//参与满减：第3件半价，满3送1 可使用95折打折券
                oldPrice = 1080* goodsAmount.intValue();
                double price1=0,price2=0;
                double discountSum1=0,discountSum2=0;
                if(goodsAmount.intValue()<3){
                    price1 = 1080* goodsAmount.intValue();
                }else if(goodsAmount.intValue()==3){
                    price1 = 1080*(goodsAmount.intValue()-1)+1080*0.5;
                    discountSum = 1080*0.5;
                }else if(goodsAmount.intValue()>3){
                    price1 = 1080* (goodsAmount.intValue()-1);
                }
                if(discountCards!=null){
                    for(int j=0;j<discountCards.size();j++){
                        String discount =(String)discountCards.get(0);
                        if("9折券".equals(discount)){
                            price2=price*0.95;
                            discountSum2=price*0.05;
                        }
                    }
                }
                if(discountSum1>discountSum2){
                    price=price1;
                    discountSum=discountSum1;
                }else{
                    price=price2;
                    discountSum=discountSum2;
                }
                OrderItemRepresentation orderItemRepresentation = new OrderItemRepresentation("002001","守扩之羽比翼双飞4.8g",new BigDecimal(1080),new BigDecimal(goodsAmount.intValue()), new BigDecimal(oldPrice));
                orderItems.add(orderItemRepresentation);
                if(discountSum>0){
                    DiscountItemRepresentation discountItemRepresentation = new DiscountItemRepresentation("002001","守扩之羽比翼双飞4.8g",new BigDecimal(discountSum));
                    discounts.add(discountItemRepresentation);
                }
            }else if("002003".equals(goodsId)){//参与满减：每满3000元减350, 每满2000减30，每满1000减10 可使用9折打折券
                oldPrice = 698* goodsAmount.intValue();
                double price1=0,price2=0;
                double discountSum1=0,discountSum2=0;
                price = 698* goodsAmount.intValue();
                if(price>=3000){
                    price1 = oldPrice-350;
                    discountSum1=350;
                }else if(price>2000){
                    price1 = oldPrice-30;
                    discountSum1=30;
                }else if(price>1000){
                    discountSum1=10;
                    price1 = price-10;
                }
                if(discountCards!=null){
                    for(int j=0;j<discountCards.size();j++){
                        String discount =(String)discountCards.get(0);
                        if("9折券".equals(discount)){
                            price2=oldPrice*0.9;
                            discountSum2=oldPrice*0.1;
                        }
                    }
                }
                if(discountSum1>discountSum2){
                    price=price1;
                    discountSum=discountSum1;
                }else{
                    price=price2;
                    discountSum=discountSum2;
                }
                OrderItemRepresentation orderItemRepresentation = new OrderItemRepresentation("002003","中国银象棋12g",new BigDecimal(698),new BigDecimal(goodsAmount.intValue()), new BigDecimal(oldPrice));
                orderItems.add(orderItemRepresentation);
                if(discountSum>0) {
                    DiscountItemRepresentation discountItemRepresentation = new DiscountItemRepresentation("002003", "中国银象棋12g", new BigDecimal(discountSum));
                    discounts.add(discountItemRepresentation);
                }
            }
            totalPaySum=totalPaySum+oldPrice;
            totalDiscountPrice = totalDiscountPrice+discountSum;
        }
        Map map = new HashMap();
        map.put("totalPaySum",totalPaySum);
        map.put("totalDiscountPrice",totalDiscountPrice);
        return map;
    }

}