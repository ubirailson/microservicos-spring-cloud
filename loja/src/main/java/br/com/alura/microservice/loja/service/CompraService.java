package br.com.alura.microservice.loja.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

import br.com.alura.microservice.loja.client.FornecedorClient;
import br.com.alura.microservice.loja.controller.dto.CompraDTO;
import br.com.alura.microservice.loja.controller.dto.InfoFornecedorDTO;
import br.com.alura.microservice.loja.controller.dto.InfoPedidoDTO;
import br.com.alura.microservice.loja.model.Compra;
import br.com.alura.microservice.loja.repository.CompraRepository;

@Service
public class CompraService {

	private static final Logger LOG = LoggerFactory.getLogger(CompraService.class);

	@Autowired
	private RestTemplate client;
	// Vai dizer quais instâncias estão disponíveis via ribbon
	@Autowired
	private DiscoveryClient eurekaClient;

	@Autowired
	private FornecedorClient fornecedorClient;
	
	@Autowired
	private CompraRepository compraRepository;

	@HystrixCommand(threadPoolKey = "getByIdThreadPool")
	public Compra getById(Long id) {
		return compraRepository.findById(id).orElse(new Compra());
	}
	
	@HystrixCommand(fallbackMethod = "realizaCompraFallback",
			threadPoolKey = "realizaCompraThreadPool")
	public Compra realizaCompra(CompraDTO compra) {

		final String estado = compra.getEndereco().getEstado();
		LOG.info("buscando informações do fornecedor de {}", estado);
		
		InfoFornecedorDTO info = fornecedorClient.getInfoPorEstado(estado);
		
		LOG.info("realizando um pedido");
		InfoPedidoDTO infoPedido = fornecedorClient.realizaPedido(compra.getItens());
		System.out.println(info.getEndereco());

		Compra compraSalva = new Compra();
		compraSalva.setPedidoId(infoPedido.getId());
		compraSalva.setTempoDePreparo(infoPedido.getTempoDePreparo());
		compraSalva.setEnderecoDestino(info.getEndereco());
		compraRepository.save(compraSalva);
		
		//Simulando demora que dispara método fallback configurado com demora padrão de 1 segundo.
//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		return compraSalva;
	}
	public Compra realizaCompraFallback(CompraDTO compra) {
		Compra compraFallback = new Compra();
		compraFallback.setEnderecoDestino(compra.getEndereco().toString());
		return compraFallback;
	}

	public void realizaCompraOld(CompraDTO compra) {

		// RestTemplate client = new RestTemplate();
//		ResponseEntity<InfoFornecedorDTO> exchange = 
//				client.exchange("http://localhost:8081/info/" + compra.getEndereco().getEstado(),
//				HttpMethod.GET, null, InfoFornecedorDTO.class);

		ResponseEntity<InfoFornecedorDTO> exchange = client.exchange(
				"http://fornecedor/info/" + compra.getEndereco().getEstado(), HttpMethod.GET, null,
				InfoFornecedorDTO.class);

		eurekaClient.getInstances("fornecedor").stream().forEach(fornecedor -> {
			System.out.println("localhost:" + fornecedor.getPort());
		});

		System.out.println(exchange.getBody().getEndereco());
	}

}
